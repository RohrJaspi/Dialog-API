package dev.dialogapi.api.glyph;

import java.util.List;

/**
 * A bitmap glyph of the pack font. Textures wider than the client's 256 px glyph limit are split
 * into several slices that are drawn next to each other.
 *
 * @param width  width of the source texture in pixels
 * @param height height of the source texture in pixels
 */
public record Glyph(List<Slice> slices, int width, int height) {

    /**
     * @param dx      horizontal position of the slice inside the glyph
     * @param advance cursor advance the client applies after drawing the slice
     */
    public record Slice(int codepoint, int dx, int advance) {}

    public Glyph {
        slices = List.copyOf(slices);
    }

    /** Cursor position after the whole glyph when it is drawn at cursor position 0. */
    public int advance() {
        Slice last = slices.getLast();
        return last.dx() + last.advance();
    }
}
