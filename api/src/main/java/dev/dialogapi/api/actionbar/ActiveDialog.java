package dev.dialogapi.api.actionbar;

import org.bukkit.entity.Player;

/** A running dialog of one player. */
public interface ActiveDialog {

    Player player();

    Dialog dialog();

    DialogNode node();

    boolean isAnswering();

    boolean isEnded();

    void end(EndReason reason);
}
