package dev.dialogapi.api.actionbar;

import dev.dialogapi.api.actionbar.Dialog;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/** Called before a dialog starts. Cancel it to prevent the dialog. */
public final class DialogStartEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Dialog dialog;
    private final @Nullable Entity npc;
    private boolean cancelled;

    public DialogStartEvent(Player player, Dialog dialog, @Nullable Entity npc) {
        super(player);
        this.dialog = dialog;
        this.npc = npc;
    }

    public Dialog getDialog() {
        return dialog;
    }

    /** The NPC the player talks to, or {@code null} if the dialog was started by command/API. */
    public @Nullable Entity getNpc() {
        return npc;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
