package dev.dialogapi.plugin.render;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;

/**
 * One line of styled text: a sequence of characters, each with its own style and pixel advance.
 * The typewriter shows a prefix of it ({@link #toComponent(Key, Style, String, int)}).
 */
public final class StyledLine {

    public static final StyledLine EMPTY = new StyledLine(new int[0], new Style[0], new int[0]);

    private final int[] codepoints;
    private final Style[] styles;
    private final int[] advances;
    private final int width;

    StyledLine(int[] codepoints, Style[] styles, int[] advances) {
        this.codepoints = codepoints;
        this.styles = styles;
        this.advances = advances;
        int w = 0;
        for (int a : advances) {
            w += a;
        }
        this.width = w;
    }

    public int length() {
        return codepoints.length;
    }

    public int width() {
        return width;
    }

    public int codepoint(int index) {
        return codepoints[index];
    }

    /** Width of the first {@code count} characters. */
    public int width(int count) {
        if (count >= codepoints.length) {
            return width;
        }
        int w = 0;
        for (int i = 0; i < count; i++) {
            w += advances[i];
        }
        return w;
    }

    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (int cp : codepoints) {
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    /**
     * Builds the component for the first {@code count} characters.
     *
     * @param font        the line font (it decides the vertical position)
     * @param baseStyle   default color/shadow, inherited by every run
     * @param leadingText characters rendered before the text (horizontal offset spaces), unstyled
     */
    public Component toComponent(Key font, Style baseStyle, String leadingText, int count) {
        TextComponent.Builder builder = Component.text().content(leadingText).style(baseStyle).font(font);
        int end = Math.min(count, codepoints.length);
        int i = 0;
        StringBuilder run = new StringBuilder();
        while (i < end) {
            Style style = styles[i];
            run.setLength(0);
            while (i < end && styles[i].equals(style)) {
                run.appendCodePoint(codepoints[i]);
                i++;
            }
            builder.append(Component.text(run.toString(), style));
        }
        return builder.build();
    }
}
