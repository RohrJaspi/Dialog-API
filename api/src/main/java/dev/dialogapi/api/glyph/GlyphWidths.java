package dev.dialogapi.api.glyph;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Pixel advance of every character of the dialog text fonts, exactly as the client computes it
 * (rightmost opaque column of the glyph cell, scaled, plus one pixel of spacing). The table is
 * filled by the resource pack generator from the very bitmaps it puts into the pack, so wrapping
 * on the server always matches what the client draws.
 */
public final class GlyphWidths {

    private final int[] bmp;
    private final Map<Integer, Integer> supplementary;

    private GlyphWidths(int[] bmp, Map<Integer, Integer> supplementary) {
        this.bmp = bmp;
        this.supplementary = supplementary;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean has(int codepoint) {
        return advance(codepoint) >= 0;
    }

    /** Advance of a character in pixels, or -1 if the font does not contain it. */
    public int advance(int codepoint) {
        if (codepoint >= 0 && codepoint < bmp.length) {
            return bmp[codepoint];
        }
        return supplementary.getOrDefault(codepoint, -1);
    }

    /** Advance including the extra pixel the client adds for bold text. */
    public int advance(int codepoint, boolean bold) {
        int advance = advance(codepoint);
        return advance < 0 ? advance : advance + (bold ? 1 : 0);
    }

    /** Width of an unstyled string; unknown characters count as 0. */
    public int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += Math.max(0, advance(cp));
            i += Character.charCount(cp);
        }
        return width;
    }

    public static final class Builder {
        private final int[] bmp = new int[0x10000];
        private final Map<Integer, Integer> supplementary = new HashMap<>();

        private Builder() {
            Arrays.fill(bmp, -1);
        }

        /** Registers a character unless an earlier provider already defined it (client semantics). */
        public Builder putIfAbsent(int codepoint, int advance) {
            if (codepoint < bmp.length) {
                if (bmp[codepoint] < 0) {
                    bmp[codepoint] = advance;
                }
            } else {
                supplementary.putIfAbsent(codepoint, advance);
            }
            return this;
        }

        public GlyphWidths build() {
            return new GlyphWidths(bmp.clone(), Map.copyOf(supplementary));
        }
    }
}
