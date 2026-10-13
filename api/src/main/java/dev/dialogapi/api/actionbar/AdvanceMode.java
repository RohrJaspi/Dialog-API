package dev.dialogapi.api.actionbar;

/** What happens after a text page has been fully revealed. */
public enum AdvanceMode {
    /** Wait for the player to confirm (right-click the NPC, or sneak). */
    CLICK,
    /** Continue automatically after the configured delay. Confirming still skips the wait. */
    AUTO
}
