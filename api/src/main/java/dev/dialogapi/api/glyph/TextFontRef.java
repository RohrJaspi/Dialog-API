package dev.dialogapi.api.glyph;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Handle to a text font registered through {@link PackAssets#text(int, int)}: the vanilla font
 * sheets drawn {@code scale} times larger, raised or lowered so that the top of a letter cell lies
 * at {@code top}. Besides the font key it knows the exact width of every character, computed from
 * the bitmaps that went into the pack, so text can be placed and fitted to the pixel.
 */
public final class TextFontRef {

    private static final String ELLIPSIS = "…";
    private static final String ASCII_ELLIPSIS = "...";

    private final int top;
    private final int scale;
    private volatile @Nullable Resolved resolved;

    private record Resolved(Key key, GlyphWidths widths, int replacement) {}

    @ApiStatus.Internal
    public TextFontRef(int top, int scale) {
        this.top = top;
        this.scale = scale;
    }

    /** Pixels below the top of the text line the letter cell starts (negative = above). */
    public int top() {
        return top;
    }

    public int scale() {
        return scale;
    }

    public boolean isResolved() {
        return resolved != null;
    }

    /** The font to put on a text component. */
    public Key key() {
        return require().key();
    }

    /** Replaces characters the font does not contain. */
    public String sanitize(String text) {
        Resolved font = require();
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> out.appendCodePoint(font.widths().has(cp) ? cp : font.replacement()));
        return out.toString();
    }

    /** Advance of the text in pixels (after {@link #sanitize}). */
    public int width(String text) {
        return require().widths().width(sanitize(text));
    }

    public boolean fits(String text, int maxWidth) {
        return width(text) <= maxWidth;
    }

    /** The text, shortened with an ellipsis if it is wider than {@code maxWidth}. */
    public String fit(String text, int maxWidth) {
        String clean = sanitize(text);
        if (width(clean) <= maxWidth) {
            return clean;
        }
        String ellipsis = require().widths().has(ELLIPSIS.codePointAt(0)) ? ELLIPSIS : ASCII_ELLIPSIS;
        int[] codepoints = clean.codePoints().toArray();
        for (int length = codepoints.length - 1; length > 0; length--) {
            String candidate = new String(codepoints, 0, length).stripTrailing() + ellipsis;
            if (width(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return "";
    }

    @ApiStatus.Internal
    public void resolve(@Nullable Key key, @Nullable GlyphWidths widths, int replacement) {
        this.resolved = key == null || widths == null ? null : new Resolved(key, widths, replacement);
    }

    private Resolved require() {
        Resolved font = resolved;
        if (font == null) {
            throw new IllegalStateException("Text font (top " + top + ", scale " + scale
                    + ") is not part of a built resource pack yet; use ResourcePack#whenReady");
        }
        return font;
    }
}
