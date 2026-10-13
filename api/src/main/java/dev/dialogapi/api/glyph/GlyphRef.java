package dev.dialogapi.api.glyph;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Handle to a glyph registered through {@link PackAssets}. The character codes only exist once the
 * resource pack has been built, so the handle is resolved after the next
 * {@link dev.dialogapi.api.pack.ResourcePackBuildEvent}; draw only after
 * {@link dev.dialogapi.api.pack.ResourcePack#whenReady(Runnable)}.
 */
public final class GlyphRef {

    private final String namespace;
    private final String id;
    private volatile @Nullable Glyph glyph;

    @ApiStatus.Internal
    public GlyphRef(String namespace, String id) {
        this.namespace = namespace;
        this.id = id;
    }

    public String namespace() {
        return namespace;
    }

    public String id() {
        return id;
    }

    public boolean isResolved() {
        return glyph != null;
    }

    /** @throws IllegalStateException if the pack that contains this glyph has not been built yet */
    public Glyph glyph() {
        Glyph resolved = glyph;
        if (resolved == null) {
            throw new IllegalStateException("Glyph " + namespace + ":" + id
                    + " is not part of a built resource pack yet; use ResourcePack#whenReady");
        }
        return resolved;
    }

    public int width() {
        return glyph().width();
    }

    public int height() {
        return glyph().height();
    }

    @ApiStatus.Internal
    public void resolve(@Nullable Glyph glyph) {
        this.glyph = glyph;
    }

    @Override
    public String toString() {
        return "GlyphRef[" + namespace + ":" + id + (glyph == null ? ", unresolved" : "") + "]";
    }
}
