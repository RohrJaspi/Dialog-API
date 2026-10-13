package dev.dialogapi.plugin.session;

import dev.dialogapi.api.actionbar.EndReason;
import dev.dialogapi.plugin.DialogApiPlugin;
import dev.dialogapi.api.actionbar.DialogStartEvent;
import dev.dialogapi.plugin.config.PluginSettings;
import dev.dialogapi.plugin.loader.DialogRegistry;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.DialogNode;
import dev.dialogapi.plugin.render.HudAssets;
import dev.dialogapi.plugin.render.HudRenderer;
import dev.dialogapi.plugin.render.TextLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Owns all running {@link DialogSession}s (at most one per player). Main thread only. */
public final class SessionManager {

    private final DialogApiPlugin plugin;
    private final PortraitResolver portraits = new PortraitResolver();
    private final Map<UUID, DialogSession> sessions = new HashMap<>();
    private @Nullable HudRenderer renderer;
    private @Nullable TextLayout textLayout;

    public SessionManager(DialogApiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Activates freshly built pack assets. Running sessions should be stopped before. */
    public void setAssets(HudAssets assets, PluginSettings settings) {
        this.renderer = new HudRenderer(assets, settings.textColor(), settings.textShadow());
        this.textLayout = new TextLayout(assets.widths(), settings.replacementChar());
    }

    public boolean isReady() {
        return renderer != null;
    }

    /**
     * Starts a dialog. An already running dialog of the player is replaced.
     *
     * @param npc    the NPC the player talks to (used for click detection and the distance check)
     * @param nodeId node to start at, or {@code null} for the dialog's start node
     * @return the session, or {@code null} if the pack is not ready or a listener cancelled the start
     */
    public @Nullable DialogSession start(Player player, Dialog dialog, @Nullable Entity npc, @Nullable String nodeId) {
        DialogNode first = nodeId == null ? dialog.startNode() : dialog.node(nodeId);
        if (first == null) {
            throw new IllegalArgumentException("Dialog '" + dialog.id() + "' has no node '" + nodeId + "'");
        }
        if (!isReady()) {
            String message = settings().message("not-ready");
            if (!message.isBlank()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(message));
            }
            return null;
        }
        DialogStartEvent event = new DialogStartEvent(player, dialog, npc);
        if (!event.callEvent()) {
            return null;
        }
        DialogSession previous = sessions.get(player.getUniqueId());
        if (previous != null) {
            previous.end(EndReason.REPLACED);
        }
        DialogSession session = new DialogSession(this, player, dialog, npc);
        sessions.put(player.getUniqueId(), session);
        session.begin(first);
        return session.isEnded() ? null : session;
    }

    public @Nullable DialogSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean stop(Player player, EndReason reason) {
        DialogSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        session.end(reason);
        return true;
    }

    public void stopAll(EndReason reason) {
        for (DialogSession session : new ArrayList<>(sessions.values())) {
            session.end(reason);
        }
    }

    public Collection<DialogSession> sessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    void remove(DialogSession session) {
        sessions.remove(session.player().getUniqueId(), session);
    }

    DialogApiPlugin plugin() {
        return plugin;
    }

    PluginSettings settings() {
        return plugin.settings();
    }

    DialogRegistry registry() {
        return plugin.registry();
    }

    public PortraitResolver portraits() {
        return portraits;
    }

    @Nullable HudRenderer renderer() {
        return renderer;
    }

    TextLayout textLayout() {
        if (textLayout == null) {
            throw new IllegalStateException("Resource pack not built yet");
        }
        return textLayout;
    }

    /** Number of dialogs currently running, for /dialogapi list. */
    public int count() {
        return sessions.size();
    }
}
