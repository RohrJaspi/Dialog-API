package dev.dialogapi.api.actionbar;

import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.EndReason;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Called after a dialog session ended. */
public final class DialogEndEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Dialog dialog;
    private final EndReason reason;

    public DialogEndEvent(Player player, Dialog dialog, EndReason reason) {
        super(player);
        this.dialog = dialog;
        this.reason = reason;
    }

    /** The dialog that was active when the session ended. */
    public Dialog getDialog() {
        return dialog;
    }

    public EndReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
