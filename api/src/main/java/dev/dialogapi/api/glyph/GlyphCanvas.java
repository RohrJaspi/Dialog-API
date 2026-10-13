package dev.dialogapi.api.glyph;

import dev.dialogapi.api.layout.HAlign;
import dev.dialogapi.api.layout.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Composes one line of text from absolutely positioned pieces.
 *
 * <p>The cursor starts at 0 and every piece is preceded by the offset characters that move it to
 * its x position. {@link #build()} returns the cursor to 0, so the line has a total advance of
 * exactly 0: a centred line (action bar, dialog body) starts drawing at the centre, a left-aligned
 * line (book page) at the left edge. Positions are therefore relative to the centre or the left edge.
 *
 * <p>The client multiplies bitmap glyphs with the text colour, so white keeps the texture colours
 * and any other colour tints it. The drop shadow of glyphs is switched off, otherwise every glyph
 * would draw a dark copy of itself.
 */
public final class GlyphCanvas {

    /** The font that holds every registered glyph (and the offset characters). */
    public static final Key GLYPHS_FONT = Key.key("dialogapi", "glyphs");
    /** A font with offset characters only. */
    public static final Key SPACE_FONT = Key.key("dialogapi", "space");

    private static final Style SPACE_STYLE = Style.style().font(SPACE_FONT).build();

    private final List<Component> parts = new ArrayList<>();
    private final StringBuilder glyphRun = new StringBuilder();
    private final int origin;
    private int cursor;

    public GlyphCanvas() {
        this(0);
    }

    /** @param origin cursor position of x = 0 */
    public GlyphCanvas(int origin) {
        this.origin = origin;
    }

    public GlyphCanvas glyph(GlyphRef glyph, int x) {
        return glyph(glyph.glyph(), x, NamedTextColor.WHITE);
    }

    public GlyphCanvas glyph(GlyphRef glyph, int x, TextColor tint) {
        return glyph(glyph.glyph(), x, tint);
    }

    /** Draws a glyph at its place in its area (see {@link Placed#x()}). */
    public GlyphCanvas glyph(Placed placed) {
        return glyph(placed.ref().glyph(), placed.x(), NamedTextColor.WHITE);
    }

    public GlyphCanvas glyph(Placed placed, TextColor tint) {
        return glyph(placed.ref().glyph(), placed.x(), tint);
    }

    public GlyphCanvas glyph(Glyph glyph, int x) {
        return glyph(glyph, x, NamedTextColor.WHITE);
    }

    public GlyphCanvas glyph(Glyph glyph, int x, TextColor tint) {
        if (!tint.equals(NamedTextColor.WHITE)) {
            flushGlyphs();
            StringBuilder run = new StringBuilder();
            appendGlyph(run, glyph, x);
            parts.add(Component.text(run.toString(), glyphStyle(tint)));
        } else {
            appendGlyph(glyphRun, glyph, x);
        }
        return this;
    }

    private void appendGlyph(StringBuilder run, Glyph glyph, int x) {
        for (Glyph.Slice slice : glyph.slices()) {
            int target = origin + x + slice.dx();
            run.append(OffsetChars.encode(target - cursor)).appendCodePoint(slice.codepoint());
            cursor = target + slice.advance();
        }
    }

    /**
     * Text in a registered text font with its left edge at {@code x}. The text keeps the normal text
     * shadow; bold is switched off because it would add a pixel to every character.
     */
    public GlyphCanvas text(String text, TextFontRef font, int x, TextColor color) {
        String clean = font.sanitize(text);
        Style style = Style.style()
                .font(font.key())
                .color(color)
                .decoration(TextDecoration.BOLD, false)
                .build();
        // the offset characters are part of every text font, so they travel in the same component
        return styledWithOffset(offset -> Component.text(offset + clean, style), font.width(clean), x);
    }

    /** Text fitted (with "…") into a slot of {@code slotWidth} pixels starting at {@code slotX}. */
    public GlyphCanvas text(String text, TextFontRef font, int slotX, int slotWidth, HAlign align, TextColor color) {
        String fitted = font.fit(text, Math.max(0, slotWidth));
        if (fitted.isEmpty()) {
            return this;
        }
        int width = font.width(fitted);
        return text(fitted, font, align.x(new Rect(slotX, 0, slotWidth, 0), width), color);
    }

    /** Text fitted into the rectangle of the slot and aligned as the slot says. */
    public GlyphCanvas text(String text, TextSlot slot, TextColor color) {
        return text(text, slot.font(), slot.rect().x(), slot.rect().width(), slot.align(), color);
    }

    /**
     * An already styled component whose total advance is {@code advance}, drawn with its left edge
     * at {@code x}. The component must contain its own font and colour. Used for text that carries
     * several styles and for objects such as player heads (advance 8).
     */
    public GlyphCanvas styled(Component component, int advance, int x) {
        flushGlyphs();
        int target = origin + x;
        String offset = OffsetChars.encode(target - cursor);
        if (!offset.isEmpty()) {
            parts.add(Component.text(offset, SPACE_STYLE));
        }
        parts.add(component);
        cursor = target + advance;
        return this;
    }

    /** Like {@link #styled}, for a component that starts with the offset characters itself. */
    public GlyphCanvas styledWithOffset(Function<String, Component> component, int advance, int x) {
        flushGlyphs();
        int target = origin + x;
        parts.add(component.apply(OffsetChars.encode(target - cursor)));
        cursor = target + advance;
        return this;
    }

    /** Returns the cursor to 0 and builds the component. */
    public Component build() {
        if (cursor != 0) {
            glyphRun.append(OffsetChars.encode(-cursor));
            cursor = 0;
        }
        flushGlyphs();
        return Component.textOfChildren(parts.toArray(Component[]::new));
    }

    /**
     * A glyph followed by spacing so that the whole component advances exactly {@code textX}
     * pixels. Put it in front of a name to get "icon + text".
     */
    public static Component inline(GlyphRef glyph, int textX) {
        GlyphCanvas canvas = new GlyphCanvas().glyph(glyph, 0);
        canvas.glyphRun.append(OffsetChars.encode(textX - canvas.cursor));
        canvas.flushGlyphs();
        return Component.textOfChildren(canvas.parts.toArray(Component[]::new));
    }

    /**
     * Plain horizontal spacing of {@code pixels} (may be negative). The font sits on a child, so
     * text appended to the result keeps the default font.
     */
    public static Component space(int pixels) {
        return Component.textOfChildren(Component.text(OffsetChars.encode(pixels), SPACE_STYLE));
    }

    /** Draws nothing and has no advance, but is not empty: keeps a trailing line from being dropped. */
    public static Component zeroWidth() {
        return Component.text(OffsetChars.encode(1) + OffsetChars.encode(-1), SPACE_STYLE);
    }

    private void flushGlyphs() {
        if (!glyphRun.isEmpty()) {
            parts.add(Component.text(glyphRun.toString(), glyphStyle(NamedTextColor.WHITE)));
            glyphRun.setLength(0);
        }
    }

    private static Style glyphStyle(TextColor color) {
        return Style.style()
                .font(GLYPHS_FONT)
                .color(color)
                .shadowColor(ShadowColor.none())
                .build();
    }
}
