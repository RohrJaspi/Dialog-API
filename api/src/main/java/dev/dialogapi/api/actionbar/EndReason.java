package dev.dialogapi.api.actionbar;

/** Why a dialog session ended. */
public enum EndReason {
    /** The dialog reached its end (last node, an end action or a question timeout without default). */
    FINISHED,
    /** Stopped by /dialogapi stop or the API. */
    STOPPED,
    /** Another dialog was started for the same player. */
    REPLACED,
    QUIT,
    DEATH,
    WORLD_CHANGE,
    /** The player walked further from the NPC than allowed. */
    WALKED_AWAY,
    /** A broken jump target (unknown node or dialog). */
    ERROR,
    RELOAD,
    PLUGIN_DISABLE
}
