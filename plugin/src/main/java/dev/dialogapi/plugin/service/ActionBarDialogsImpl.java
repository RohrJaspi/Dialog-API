package dev.dialogapi.plugin.service;

import dev.dialogapi.api.actionbar.ActionBarDialogs;
import dev.dialogapi.api.actionbar.ActiveDialog;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.EndReason;
import dev.dialogapi.plugin.DialogApiPlugin;
import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** {@link ActionBarDialogs} on top of the plugin's registry, sessions and bindings. */
public final class ActionBarDialogsImpl implements ActionBarDialogs {

    private final DialogApiPlugin plugin;

    public ActionBarDialogsImpl(DialogApiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean start(Player player, String dialogId) {
        return start(player, dialogId, null);
    }

    @Override
    public boolean start(Player player, String dialogId, @Nullable Entity npc) {
        Dialog dialog = plugin.registry().get(dialogId);
        if (dialog == null) {
            throw new IllegalArgumentException("Unknown dialog '" + dialogId + "'");
        }
        return start(player, dialog, npc, null);
    }

    @Override
    public boolean start(Player player, Dialog dialog) {
        return start(player, dialog, null, null);
    }

    @Override
    public boolean start(Player player, Dialog dialog, @Nullable Entity npc, @Nullable String nodeId) {
        return plugin.sessions().start(player, dialog, npc, nodeId) != null;
    }

    @Override
    public boolean stop(Player player) {
        return plugin.sessions().stop(player, EndReason.STOPPED);
    }

    @Override
    public boolean isInDialog(Player player) {
        return plugin.sessions().get(player) != null;
    }

    @Override
    public @Nullable ActiveDialog session(Player player) {
        return plugin.sessions().get(player);
    }

    @Override
    public void register(Dialog dialog) {
        plugin.registry().register(dialog);
        plugin.prefetchPortraits(dialog);
    }

    @Override
    public boolean unregister(String dialogId) {
        return plugin.registry().unregister(dialogId);
    }

    @Override
    public @Nullable Dialog dialog(String dialogId) {
        return plugin.registry().get(dialogId);
    }

    @Override
    public Set<String> dialogIds() {
        return plugin.registry().ids();
    }

    @Override
    public void bind(Entity entity, String dialogId) {
        plugin.bindings().bind(entity, dialogId);
    }

    @Override
    public boolean unbind(Entity entity) {
        return plugin.bindings().unbind(entity);
    }

    @Override
    public void bindTag(String scoreboardTag, String dialogId) {
        plugin.bindings().bindTag(scoreboardTag, dialogId);
    }
}
