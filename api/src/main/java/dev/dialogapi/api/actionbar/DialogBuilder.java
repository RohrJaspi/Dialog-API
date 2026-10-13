package dev.dialogapi.api.actionbar;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for {@link Dialog}s.
 *
 * <pre>{@code
 * Dialog dialog = Dialog.builder("stats_house")
 *     .speaker("<gold>MaximDe").portrait("MaximDe")
 *     .text("Welcome to the Stats House! Here you can look at the leaderboards of Duels.")
 *     .question("menu", "What do you want to know?")
 *         .answer("How does that work?", "explain")
 *         .answer("How do I rank up?", "rankup")
 *         .answer("Alright then!", Dialog.END)
 *     .text("explain", "Every duel you win gives you rating points.").then("menu")
 *     .text("rankup", "Win duels to climb the ladder!").then("menu")
 *     .build();
 * DialogManager.register(dialog);
 * }</pre>
 *
 * Node methods ({@code text}, {@code question}) return a step builder for that node. Step builders
 * forward {@code text(..)}, {@code question(..)} and {@code build()} to this builder, so a whole
 * dialog can be written as one chain.
 */
public final class DialogBuilder {

    private final String id;
    private @Nullable String speaker;
    private @Nullable Portrait portrait;
    private Pacing pacing = Pacing.EMPTY;
    private @Nullable Double maxDistance;
    private @Nullable String start;
    private final List<NodeStep<?>> steps = new ArrayList<>();

    public DialogBuilder(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    /** Speaker name shown in the name tab (MiniMessage). */
    public DialogBuilder speaker(String miniMessage) {
        this.speaker = miniMessage;
        return this;
    }

    /**
     * Portrait head: a player name, UUID, base64 texture value or {@code %player%}.
     *
     * @throws IllegalArgumentException if the value is not a valid player name, UUID or texture
     */
    public DialogBuilder portrait(String value) {
        this.portrait = Portrait.parse(value);
        return this;
    }

    public DialogBuilder portrait(@Nullable Portrait portrait) {
        this.portrait = portrait;
        return this;
    }

    public DialogBuilder typewriter(TypewriterMode mode, int ticksPerStep) {
        this.pacing = pacing.withMode(mode).withSpeed(ticksPerStep);
        return this;
    }

    public DialogBuilder sound(SoundSpec sound) {
        this.pacing = pacing.withSound(sound);
        return this;
    }

    public DialogBuilder noSound() {
        return sound(SoundSpec.NONE);
    }

    /** All text nodes continue automatically {@code delayTicks} after their text is fully shown. */
    public DialogBuilder autoContinue(int delayTicks) {
        this.pacing = pacing.withAdvance(AdvanceMode.AUTO).withAutoDelay(delayTicks);
        return this;
    }

    public DialogBuilder clickToContinue() {
        this.pacing = pacing.withAdvance(AdvanceMode.CLICK);
        return this;
    }

    public DialogBuilder pacing(Pacing pacing) {
        this.pacing = Objects.requireNonNull(pacing, "pacing");
        return this;
    }

    /** End the dialog when the player walks further than this from the NPC (-1 = never). */
    public DialogBuilder maxDistance(double blocks) {
        this.maxDistance = blocks;
        return this;
    }

    /** Node to start at (default: the first node). */
    public DialogBuilder start(String nodeId) {
        this.start = nodeId;
        return this;
    }

    public TextStep text(String text) {
        return text(null, text);
    }

    public TextStep text(@Nullable String nodeId, String text) {
        TextStep step = new TextStep(this, nodeId != null ? nodeId : nextId(), text);
        steps.add(step);
        return step;
    }

    public QuestionStep question(String text) {
        return question(null, text);
    }

    public QuestionStep question(@Nullable String nodeId, String text) {
        QuestionStep step = new QuestionStep(this, nodeId != null ? nodeId : nextId(), text);
        steps.add(step);
        return step;
    }

    /**
     * Builds and validates the dialog.
     *
     * @throws IllegalStateException if a node jumps to an unknown node or a question has no answers
     */
    public Dialog build() {
        List<DialogNode> nodes = new ArrayList<>(steps.size());
        for (NodeStep<?> step : steps) {
            nodes.add(step.toNode());
        }
        Dialog dialog = new Dialog(id, speaker, portrait, pacing, maxDistance, start, nodes);
        List<String> problems = dialog.validate();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid dialog '" + id + "': " + String.join("; ", problems));
        }
        return dialog;
    }

    private String nextId() {
        return "node_" + (steps.size() + 1);
    }

    /** Settings shared by text and question steps. */
    public abstract static sealed class NodeStep<S extends NodeStep<S>> permits TextStep, QuestionStep {
        final DialogBuilder parent;
        String id;
        final List<String> pages = new ArrayList<>();
        @Nullable String speaker;
        @Nullable Portrait portrait;
        Pacing pacing = Pacing.EMPTY;
        final List<DialogAction> onEnter = new ArrayList<>();

        NodeStep(DialogBuilder parent, String id, String text) {
            this.parent = parent;
            this.id = id;
            this.pages.add(text);
        }

        abstract S self();

        abstract DialogNode toNode();

        /** Renames this node (useful when it was created without an id). */
        public S id(String id) {
            this.id = Objects.requireNonNull(id, "id");
            return self();
        }

        /** Adds another page of text to this node. */
        public S page(String text) {
            pages.add(text);
            return self();
        }

        public S speaker(String miniMessage) {
            this.speaker = miniMessage;
            return self();
        }

        public S portrait(String value) {
            this.portrait = Portrait.parse(value);
            return self();
        }

        public S portrait(Portrait portrait) {
            this.portrait = portrait;
            return self();
        }

        public S typewriter(TypewriterMode mode, int ticksPerStep) {
            this.pacing = pacing.withMode(mode).withSpeed(ticksPerStep);
            return self();
        }

        public S sound(SoundSpec sound) {
            this.pacing = pacing.withSound(sound);
            return self();
        }

        public S noSound() {
            return sound(SoundSpec.NONE);
        }

        /** Commands, messages or callbacks executed when this node starts. */
        public S onEnter(DialogAction... actions) {
            this.onEnter.addAll(Arrays.asList(actions));
            return self();
        }

        public TextStep text(String text) {
            return parent.text(text);
        }

        public TextStep text(String nodeId, String text) {
            return parent.text(nodeId, text);
        }

        public QuestionStep question(String text) {
            return parent.question(text);
        }

        public QuestionStep question(String nodeId, String text) {
            return parent.question(nodeId, text);
        }

        public Dialog build() {
            return parent.build();
        }
    }

    /** Builder step for a {@link TextNode}. */
    public static final class TextStep extends NodeStep<TextStep> {
        private final List<DialogAction> then = new ArrayList<>();

        TextStep(DialogBuilder parent, String id, String text) {
            super(parent, id, text);
        }

        @Override
        TextStep self() {
            return this;
        }

        /** Where to continue after this node: a node id, {@link Dialog#END} or {@code @dialog:<id>}. */
        public TextStep then(String target) {
            then.add(DialogAction.target(target));
            return this;
        }

        public TextStep then(DialogAction... actions) {
            then.addAll(Arrays.asList(actions));
            return this;
        }

        public TextStep thenEnd() {
            return then(DialogAction.end());
        }

        public TextStep autoContinue(int delayTicks) {
            this.pacing = pacing.withAdvance(AdvanceMode.AUTO).withAutoDelay(delayTicks);
            return this;
        }

        public TextStep clickToContinue() {
            this.pacing = pacing.withAdvance(AdvanceMode.CLICK);
            return this;
        }

        @Override
        DialogNode toNode() {
            return new TextNode(id, pages, speaker, portrait, pacing, onEnter, then);
        }
    }

    /** Builder step for a {@link QuestionNode}. */
    public static final class QuestionStep extends NodeStep<QuestionStep> {
        private final List<Answer> answers = new ArrayList<>();
        private int timeoutTicks;
        private int defaultAnswer = -1;

        QuestionStep(DialogBuilder parent, String id, String text) {
            super(parent, id, text);
        }

        @Override
        QuestionStep self() {
            return this;
        }

        /** Adds an answer that jumps to a node id, {@link Dialog#END} or {@code @dialog:<id>}. */
        public QuestionStep answer(String text, String target) {
            answers.add(new Answer(text, List.of(DialogAction.target(target))));
            return this;
        }

        /** Adds an answer with arbitrary actions (commands, messages, callbacks, flow). */
        public QuestionStep answer(String text, DialogAction... actions) {
            answers.add(new Answer(text, Arrays.asList(actions)));
            return this;
        }

        /**
         * Chooses an answer automatically after {@code ticks}.
         *
         * @param defaultAnswer one-based answer number, or 0 to end the dialog on timeout
         */
        public QuestionStep timeout(int ticks, int defaultAnswer) {
            this.timeoutTicks = ticks;
            this.defaultAnswer = defaultAnswer - 1;
            return this;
        }

        @Override
        DialogNode toNode() {
            return new QuestionNode(id, pages, speaker, portrait, pacing, onEnter, answers, timeoutTicks, defaultAnswer);
        }
    }
}
