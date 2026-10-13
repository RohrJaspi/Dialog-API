package dev.dialogapi.plugin.session;

import dev.dialogapi.api.actionbar.EndReason;
import dev.dialogapi.api.actionbar.ActiveDialog;
import dev.dialogapi.api.actionbar.DialogAnswerEvent;
import dev.dialogapi.api.actionbar.DialogEndEvent;
import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.config.PluginSettings;
import dev.dialogapi.api.actionbar.AdvanceMode;
import dev.dialogapi.api.actionbar.Answer;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.DialogAction;
import dev.dialogapi.api.actionbar.DialogNode;
import dev.dialogapi.api.actionbar.Pacing;
import dev.dialogapi.api.actionbar.Portrait;
import dev.dialogapi.api.actionbar.QuestionNode;
import dev.dialogapi.api.actionbar.SoundSpec;
import dev.dialogapi.api.actionbar.TextNode;
import dev.dialogapi.api.actionbar.TypewriterMode;
import dev.dialogapi.plugin.render.HudFrame;
import dev.dialogapi.plugin.render.HudRenderer;
import dev.dialogapi.plugin.render.Page;
import dev.dialogapi.plugin.render.StyledLine;
import dev.dialogapi.plugin.render.TextLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * One player's running dialog. Driven by a 1-tick repeating task; all methods run on the main thread.
 *
 * <pre>
 *   enterNode ─► startPage ─► TYPING ──(all text shown)──► WAITING ──confirm/auto──► next page / next node
 *                                    └─(question, last page)─► ANSWERING ──confirm/timeout──► answer actions
 * </pre>
 */
public final class DialogSession implements ActiveDialog {

    enum Phase { TYPING, WAITING, ANSWERING, ENDED }

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final SessionManager manager;
    private final Player player;
    private final @Nullable UUID npcId;
    private Location anchor;
    private @Nullable BukkitTask task;

    private Dialog dialog;
    private DialogNode node;
    private Pacing pacing = Pacing.EMPTY;
    private @Nullable StyledLine name;
    private @Nullable PlayerHeadObjectContents head;

    private List<Page> pages = List.of();
    private int pageIndex;
    private Page page = new Page(List.of());
    private int[] stops = new int[0];
    private boolean[] wordStarts = new boolean[0];
    private int step;
    private int visible;
    private int cooldown;

    private Phase phase = Phase.TYPING;
    private int waitTicks;
    private int answerTicks;
    private int selected;
    private List<StyledLine> answers = List.of();
    private List<StyledLine> selectedAnswers = List.of();

    private long ticks;
    private long lastInputTick;
    private boolean dirty = true;
    private int sinceRender;
    private boolean indicatorOn = true;
    private int blinkCounter;

    private int lastReportedSlot = -1;
    private long lastScrollNanos;

    DialogSession(SessionManager manager, Player player, Dialog dialog, @Nullable Entity npc) {
        this.manager = manager;
        this.player = player;
        this.dialog = dialog;
        this.node = dialog.startNode();
        this.npcId = npc != null ? npc.getUniqueId() : null;
        this.anchor = npc != null ? npc.getLocation() : player.getLocation();
    }

    // ------------------------------------------------------------------------------------ lifecycle

    void begin(DialogNode first) {
        task = Bukkit.getScheduler().runTaskTimer(manager.plugin(), this::tick, 1L, 1L);
        enterNode(first);
        if (phase != Phase.ENDED) {
            render();
        }
    }

    /** Ends the session (idempotent). */
    public void end(EndReason reason) {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        if (task != null) {
            task.cancel();
            task = null;
        }
        manager.remove(this);
        if (player.isOnline() && reason != EndReason.REPLACED) {
            player.sendActionBar(Component.empty());
        }
        Bukkit.getPluginManager().callEvent(new DialogEndEvent(player, dialog, reason));
    }

    public boolean isEnded() {
        return phase == Phase.ENDED;
    }

    public Player player() {
        return player;
    }

    public Dialog dialog() {
        return dialog;
    }

    public DialogNode node() {
        return node;
    }

    /** True while the answer box is shown and the hotbar is used for selecting answers. */
    public boolean isAnswering() {
        return phase == Phase.ANSWERING;
    }

    public boolean hasNpc() {
        return npcId != null;
    }

    public boolean isNpc(Entity entity) {
        return npcId != null && npcId.equals(entity.getUniqueId());
    }

    // ------------------------------------------------------------------------------------ input

    /** Right-click on the NPC / sneak: skip typing, continue, or confirm the selected answer. */
    public void confirm() {
        if (phase == Phase.ENDED || ticks - lastInputTick < settings().inputCooldownTicks()) {
            return;
        }
        lastInputTick = ticks;
        switch (phase) {
            case TYPING -> {
                if (settings().allowSkip()) {
                    finishTyping();
                    render();
                }
            }
            case WAITING -> {
                play(settings().confirmSound(), false);
                advance();
            }
            case ANSWERING -> choose(selected, false);
            default -> {
            }
        }
    }

    /**
     * Hotbar scroll while answering. The server cancels the slot change and sends the old slot
     * back, but a fast second scroll can arrive before the client applied that correction, so the
     * delta is measured against the last slot the client reported if it was very recent.
     */
    public void onHotbarScroll(int previousSlot, int newSlot) {
        if (phase != Phase.ANSWERING) {
            return;
        }
        long now = System.nanoTime();
        long sinceLastMs = (now - lastScrollNanos) / 1_000_000L;
        boolean corrected = lastReportedSlot < 0 || sinceLastMs > player.getPing() + 60L;
        int base = corrected ? previousSlot : lastReportedSlot;
        lastReportedSlot = newSlot;
        lastScrollNanos = now;

        int delta = Math.floorMod(newSlot - base + 4, 9) - 4; // -4..4, handles the 8 <-> 0 wrap
        if (delta == 1 || delta == -1) {
            select(selected + delta);
        } else if (delta != 0 && settings().numberKeys() && newSlot < answers.size()) {
            select(newSlot); // number key 1-9
        }
    }

    private void select(int index) {
        int count = answers.size();
        if (count == 0) {
            return;
        }
        int target = settings().wrapSelection() ? Math.floorMod(index, count) : Math.clamp(index, 0, count - 1);
        if (target != selected) {
            selected = target;
            play(settings().selectSound(), false);
            render();
        }
    }

    // ------------------------------------------------------------------------------------ ticking

    private void tick() {
        if (phase == Phase.ENDED) {
            return;
        }
        ticks++;
        if (!player.isOnline()) {
            end(EndReason.QUIT);
            return;
        }
        if (ticks % 10 == 0 && walkedAway()) {
            return;
        }
        switch (phase) {
            case TYPING -> tickTyping();
            case WAITING -> tickWaiting();
            case ANSWERING -> tickAnswering();
            default -> {
            }
        }
        if (phase == Phase.ENDED) {
            return;
        }
        sinceRender++;
        if (dirty || sinceRender >= settings().refreshTicks()) {
            render();
        }
    }

    private void tickTyping() {
        if (--cooldown > 0) {
            return;
        }
        visible = stops[step];
        boolean startsWord = wordStarts[step];
        step++;
        dirty = true;
        SoundSpec sound = pacing.sound();
        if (sound != null && sound.enabled()
                && (pacing.mode() == TypewriterMode.WORD || sound.trigger() == SoundSpec.Trigger.LETTER || startsWord)) {
            play(sound, true);
        }
        if (step >= stops.length) {
            finishTyping();
        } else {
            cooldown = Math.max(1, speed()) + punctuationDelay(page.codepointAt(visible - 1));
        }
    }

    private void tickWaiting() {
        int blink = settings().indicatorBlinkTicks();
        if (blink > 0 && ++blinkCounter >= blink) {
            blinkCounter = 0;
            indicatorOn = !indicatorOn;
            dirty = true;
        }
        if (waitTicks >= 0 && --waitTicks < 0) {
            advance();
        }
    }

    private void tickAnswering() {
        QuestionNode question = (QuestionNode) node;
        if (question.timeoutTicks() > 0 && ++answerTicks >= question.timeoutTicks()) {
            if (question.defaultAnswer() >= 0 && question.defaultAnswer() < answers.size()) {
                choose(question.defaultAnswer(), true);
            } else {
                end(EndReason.FINISHED);
            }
        }
    }

    private int speed() {
        Integer speed = pacing.speed();
        return speed != null ? speed : 1;
    }

    private int punctuationDelay(int codepoint) {
        Integer delay = pacing.punctuationDelay();
        if (delay == null || delay <= 0) {
            return 0;
        }
        return switch (codepoint) {
            case '.', '!', '?', '…' -> delay;
            case ',', ';', ':' -> delay / 2;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------------------------ flow

    private void enterNode(DialogNode next) {
        this.node = next;
        this.pacing = next.pacing().orElse(dialog.pacing()).orElse(settings().defaultPacing());

        String speaker = next.speaker() != null ? next.speaker() : dialog.speaker();
        name = speaker == null || speaker.isBlank() ? null
                : layout().singleLine(parse(speaker), hudLayout().nameMaxWidth());
        Portrait portrait = next.portrait() != null ? next.portrait() : dialog.portrait();
        head = manager.portraits().resolve(portrait, player);

        for (DialogAction action : next.onEnter()) {
            if (phase == Phase.ENDED) {
                return;
            }
            if (!action.isFlow()) {
                runSideEffect(action);
            }
        }
        if (phase == Phase.ENDED) {
            return;
        }
        List<Component> texts = new ArrayList<>(next.pages().size());
        for (String text : next.pages()) {
            texts.add(parse(text));
        }
        pages = layout().paginate(texts, hudLayout().textWidth(), hudLayout().maxLines());
        pageIndex = 0;
        startPage();
    }

    private void startPage() {
        page = pages.get(pageIndex);
        computeStops();
        step = 0;
        visible = 0;
        cooldown = 1;
        answers = List.of();
        selectedAnswers = List.of();
        phase = Phase.TYPING;
        dirty = true;
        if (speed() <= 0 || stops.length == 0) {
            finishTyping();
        }
    }

    /** Typewriter stop positions: after every letter, or after every word. Words never cross lines. */
    private void computeStops() {
        List<Integer> stopList = new ArrayList<>();
        List<Boolean> startList = new ArrayList<>();
        boolean words = pacing.mode() == TypewriterMode.WORD;
        int offset = 0;
        for (StyledLine line : page.lines()) {
            int length = line.length();
            if (words) {
                int i = 0;
                while (i < length) {
                    while (i < length && line.codepoint(i) == ' ') {
                        i++;
                    }
                    if (i >= length) {
                        break;
                    }
                    while (i < length && line.codepoint(i) != ' ') {
                        i++;
                    }
                    stopList.add(offset + i);
                    startList.add(true);
                }
            } else {
                for (int i = 0; i < length; i++) {
                    if (line.codepoint(i) != ' ') {
                        stopList.add(offset + i + 1);
                        startList.add(i == 0 || line.codepoint(i - 1) == ' ');
                    }
                }
            }
            offset += length;
        }
        if (stopList.isEmpty() || stopList.getLast() < offset) {
            stopList.add(offset);
            startList.add(false);
        }
        stops = stopList.stream().mapToInt(Integer::intValue).toArray();
        wordStarts = new boolean[startList.size()];
        for (int i = 0; i < wordStarts.length; i++) {
            wordStarts[i] = startList.get(i);
        }
    }

    private void finishTyping() {
        visible = page.length();
        step = stops.length;
        dirty = true;
        boolean lastPage = pageIndex >= pages.size() - 1;
        if (node instanceof QuestionNode question && lastPage) {
            phase = Phase.ANSWERING;
            answerTicks = 0;
            selected = 0;
            lastReportedSlot = -1;
            prepareAnswers(question);
        } else {
            phase = Phase.WAITING;
            indicatorOn = true;
            blinkCounter = 0;
            Integer delay = pacing.autoDelay();
            waitTicks = pacing.advance() == AdvanceMode.AUTO ? Math.max(0, delay != null ? delay : 0) : -1;
        }
    }

    private void prepareAnswers(QuestionNode question) {
        HudLayout hud = hudLayout();
        int count = Math.min(question.answers().size(), hud.maxAnswers());
        List<StyledLine> normal = new ArrayList<>(count);
        List<StyledLine> highlighted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Component answer = parse(question.answers().get(i).text());
            normal.add(layout().singleLine(formatAnswer(settings().answerFormat(), i, answer), hud.answerTextWidth()));
            highlighted.add(layout().singleLine(formatAnswer(settings().selectedAnswerFormat(), i, answer), hud.answerTextWidth()));
        }
        answers = normal;
        selectedAnswers = highlighted;
    }

    private Component formatAnswer(String format, int index, Component answer) {
        return MINI_MESSAGE.deserialize(format,
                Placeholder.unparsed("number", Integer.toString(index + 1)),
                Placeholder.component("answer", answer));
    }

    /** Continue after a fully shown page: next page, or leave the node. */
    private void advance() {
        if (pageIndex + 1 < pages.size()) {
            pageIndex++;
            startPage();
            render();
            return;
        }
        execute(node instanceof TextNode text ? text.then() : List.of());
    }

    private void choose(int index, boolean timedOut) {
        QuestionNode question = (QuestionNode) node;
        Answer answer = question.answers().get(index);
        if (!timedOut) {
            play(settings().confirmSound(), false);
        }
        Bukkit.getPluginManager().callEvent(new DialogAnswerEvent(player, dialog, question, index, timedOut));
        if (phase != Phase.ENDED && node == question) {
            execute(answer.actions());
        }
    }

    /** Runs side effects in order, then follows the last flow action (default: next node or end). */
    private void execute(List<DialogAction> actions) {
        DialogAction flow = null;
        for (DialogAction action : actions) {
            if (phase == Phase.ENDED) {
                return;
            }
            if (action.isFlow()) {
                flow = action;
            } else {
                runSideEffect(action);
            }
        }
        if (phase == Phase.ENDED) {
            return;
        }
        switch (flow) {
            case null -> {
                DialogNode next = dialog.nodeAfter(node.id());
                if (next == null) {
                    end(EndReason.FINISHED);
                } else {
                    enterNode(next);
                }
            }
            case DialogAction.GoTo(String target) -> {
                DialogNode next = dialog.node(target);
                if (next == null) {
                    fail("unknown node '" + target + "'");
                } else {
                    enterNode(next);
                }
            }
            case DialogAction.StartDialog(String dialogId, String nodeId) -> {
                Dialog other = manager.registry().get(dialogId);
                DialogNode next = other == null ? null : nodeId == null ? other.startNode() : other.node(nodeId);
                if (next == null) {
                    fail("unknown dialog/node '" + dialogId + (nodeId != null ? "#" + nodeId : "") + "'");
                } else {
                    dialog = other;
                    enterNode(next);
                }
            }
            case DialogAction.End ignored -> end(EndReason.FINISHED);
            default -> {
            }
        }
        if (phase != Phase.ENDED) {
            render();
        }
    }

    private void runSideEffect(DialogAction action) {
        try {
            switch (action) {
                case DialogAction.ConsoleCommand(String command) ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(command));
                case DialogAction.PlayerCommand(String command) -> player.performCommand(placeholders(command));
                case DialogAction.Message(String message) -> player.sendMessage(parse(message));
                case DialogAction.Callback(var callback) -> callback.accept(player);
                default -> {
                }
            }
        } catch (RuntimeException e) {
            manager.plugin().getLogger().log(Level.WARNING,
                    "Action " + action + " of dialog '" + dialog.id() + "' node '" + node.id() + "' failed", e);
        }
    }

    private void fail(String problem) {
        manager.plugin().getLogger().warning("Dialog '" + dialog.id() + "' node '" + node.id() + "' jumps to " + problem);
        end(EndReason.ERROR);
    }

    private boolean walkedAway() {
        double max = dialog.maxDistance() != null ? dialog.maxDistance() : settings().maxDistance();
        if (max < 0) {
            return false;
        }
        if (npcId != null) {
            Entity npc = Bukkit.getEntity(npcId);
            if (npc != null && npc.isValid()) {
                anchor = npc.getLocation();
            }
        }
        Location location = player.getLocation();
        if (location.getWorld() != anchor.getWorld() || location.distanceSquared(anchor) > max * max) {
            String message = settings().message("walked-away");
            if (!message.isBlank()) {
                player.sendMessage(parse(message));
            }
            end(EndReason.WALKED_AWAY);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------ output

    private void render() {
        if (phase == Phase.ENDED) {
            return;
        }
        HudRenderer renderer = manager.renderer();
        if (renderer == null) {
            return;
        }
        List<StyledLine> shownAnswers = List.of();
        if (phase == Phase.ANSWERING) {
            shownAnswers = new ArrayList<>(answers);
            shownAnswers.set(selected, selectedAnswers.get(selected));
        }
        HudFrame frame = new HudFrame(name, head, page, visible,
                phase == Phase.WAITING && indicatorOn, shownAnswers, selected);
        player.sendActionBar(renderer.render(frame));
        dirty = false;
        sinceRender = 0;
    }

    private void play(@Nullable SoundSpec sound, boolean vary) {
        if (sound == null || !sound.enabled()) {
            return;
        }
        float pitch = sound.pitch();
        if (vary && sound.pitchVariance() > 0f) {
            pitch += (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * sound.pitchVariance();
        }
        player.playSound(Sound.sound(sound.key(), sound.source(), sound.volume(), Math.clamp(pitch, 0.5f, 2f)),
                Sound.Emitter.self());
    }

    private Component parse(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage, Placeholder.unparsed("player", player.getName()));
    }

    private String placeholders(String command) {
        String result = command.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString());
        return result.startsWith("/") ? result.substring(1) : result;
    }

    private PluginSettings settings() {
        return manager.settings();
    }

    private TextLayout layout() {
        return manager.textLayout();
    }

    private HudLayout hudLayout() {
        return manager.renderer().assets().layout();
    }
}
