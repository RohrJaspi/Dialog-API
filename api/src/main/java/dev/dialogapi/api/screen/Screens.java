package dev.dialogapi.api.screen;

import io.papermc.paper.dialog.Dialog;
import org.bukkit.entity.Player;

/** Shows {@link Screen}s. */
public interface Screens {

    void show(Player player, Screen screen);

    /** Closes the dialog screen the player currently has open. */
    void close(Player player);

    /** The Paper dialog behind a screen, for features DialogAPI does not wrap. Button callbacks stay active. */
    Dialog toPaperDialog(Screen screen);
}
