package dev.dialogapi.api.pack;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired on the main thread after a rebuilt pack has been applied and all glyph handles are resolved. */
public final class ResourcePackBuildEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String sha1;
    private final boolean changed;

    public ResourcePackBuildEvent(String sha1, boolean changed) {
        this.sha1 = sha1;
        this.changed = changed;
    }

    public String getSha1() {
        return sha1;
    }

    /** @return whether the pack differs from the previous build */
    public boolean isChanged() {
        return changed;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
