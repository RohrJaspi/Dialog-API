package dev.dialogapi.api.glyph;

import dev.dialogapi.api.layout.HAlign;
import dev.dialogapi.api.layout.Rect;

/**
 * A line of text in a rectangle: the font (whose height is baked in) and the area the text is fitted
 * and aligned in ({@link GlyphCanvas#text(String, TextSlot, net.kyori.adventure.text.format.TextColor)}).
 */
public record TextSlot(TextFontRef font, Rect rect, HAlign align) {}
