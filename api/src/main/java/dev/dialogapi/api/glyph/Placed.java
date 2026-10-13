package dev.dialogapi.api.glyph;

import dev.dialogapi.api.layout.HAlign;
import dev.dialogapi.api.layout.Rect;

/**
 * A glyph together with the area it belongs to. Boxes fill their area; an image is aligned inside it,
 * and because its width is only known once the pack is built, the final x is computed when it is drawn
 * ({@link GlyphCanvas#glyph(Placed)}).
 */
public record Placed(GlyphRef ref, Rect area, HAlign align) {

    /** The x the glyph is drawn at; needs a resolved {@link #ref()}. */
    public int x() {
        return align.x(area, ref.width());
    }
}
