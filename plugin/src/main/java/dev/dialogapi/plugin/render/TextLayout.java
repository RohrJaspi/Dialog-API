package dev.dialogapi.plugin.render;

import dev.dialogapi.api.glyph.GlyphWidths;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Turns (MiniMessage-parsed) components into pixel-measured lines: flattening, word wrapping,
 * pagination and truncation. All measurements use the {@link GlyphWidths} table of the pack.
 *
 * <p>Only styles that render correctly on a shifted font survive: color, bold, italic, obfuscated
 * and shadow color. Underline and strikethrough are drawn by the client relative to the vanilla
 * action bar line (not the shifted glyphs), so they are removed.
 */
public final class TextLayout {

    private static final TextDecoration[] KEPT_DECORATIONS =
            {TextDecoration.BOLD, TextDecoration.ITALIC, TextDecoration.OBFUSCATED};

    private final GlyphWidths widths;
    private final int replacement;

    public TextLayout(GlyphWidths widths, int replacement) {
        this.widths = widths;
        this.replacement = widths.has(replacement) ? replacement : (widths.has('?') ? '?' : -1);
    }

    public GlyphWidths widths() {
        return widths;
    }

    /** Wraps every component to {@code maxWidth} and splits the lines into pages of {@code maxLines}. */
    public List<Page> paginate(List<Component> texts, int maxWidth, int maxLines) {
        List<Page> pages = new ArrayList<>();
        for (Component text : texts) {
            List<StyledLine> lines = wrap(text, maxWidth);
            for (int i = 0; i < lines.size(); i += maxLines) {
                pages.add(new Page(lines.subList(i, Math.min(lines.size(), i + maxLines))));
            }
        }
        if (pages.isEmpty()) {
            pages.add(new Page(List.of()));
        }
        return pages;
    }

    /** Greedy word wrap. Explicit newlines are kept, words longer than a line are split. */
    public List<StyledLine> wrap(Component text, int maxWidth) {
        Cells cells = flatten(text);
        List<StyledLine> lines = new ArrayList<>();
        LineBuffer line = new LineBuffer(cells);
        int n = cells.size();
        int spaceStart = -1;
        int spaceEnd = -1;
        int i = 0;
        while (i < n) {
            int cp = cells.codepoints.get(i);
            if (cp == '\n') {
                lines.add(line.build());
                line = new LineBuffer(cells);
                spaceStart = -1;
                i++;
                continue;
            }
            if (cp == ' ') {
                spaceStart = i;
                while (i < n && cells.codepoints.get(i) == ' ') {
                    i++;
                }
                spaceEnd = i;
                continue;
            }
            int wordEnd = i;
            while (wordEnd < n && cells.codepoints.get(wordEnd) != ' ' && cells.codepoints.get(wordEnd) != '\n') {
                wordEnd++;
            }
            int wordWidth = cells.width(i, wordEnd);
            int spaceWidth = spaceStart >= 0 ? cells.width(spaceStart, spaceEnd) : 0;
            if (!line.isEmpty() && line.width + spaceWidth + wordWidth > maxWidth) {
                lines.add(line.build());
                line = new LineBuffer(cells);
            } else if (!line.isEmpty() && spaceStart >= 0) {
                line.add(spaceStart, spaceEnd);
            }
            spaceStart = -1;
            if (wordWidth > maxWidth) {
                for (int k = i; k < wordEnd; k++) {
                    if (!line.isEmpty() && line.width + cells.advances.get(k) > maxWidth) {
                        lines.add(line.build());
                        line = new LineBuffer(cells);
                    }
                    line.add(k, k + 1);
                }
            } else {
                line.add(i, wordEnd);
            }
            i = wordEnd;
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.build());
        }
        return lines;
    }

    /** A single line (newlines become spaces), cut with an ellipsis when wider than {@code maxWidth}. */
    public StyledLine singleLine(Component text, int maxWidth) {
        Cells cells = flatten(text);
        for (int i = 0; i < cells.size(); i++) {
            if (cells.codepoints.get(i) == '\n') {
                cells.codepoints.set(i, (int) ' ');
                cells.advances.set(i, Math.max(0, widths.advance(' ')));
            }
        }
        if (cells.width(0, cells.size()) <= maxWidth) {
            return new LineBuffer(cells).addAll().build();
        }
        String ellipsis = widths.has('…') ? "…" : "...";
        int ellipsisWidth = widths.width(ellipsis);
        int end = cells.size();
        while (end > 0 && (cells.width(0, end) + ellipsisWidth > maxWidth || cells.codepoints.get(end - 1) == ' ')) {
            end--;
        }
        Style style = end > 0 ? cells.styles.get(end - 1) : Style.empty();
        Cells cut = new Cells();
        for (int i = 0; i < end; i++) {
            cut.add(cells.codepoints.get(i), cells.styles.get(i), cells.advances.get(i));
        }
        boolean bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        ellipsis.codePoints().forEach(cp -> cut.add(cp, style, widths.advance(cp, bold)));
        return new LineBuffer(cut).addAll().build();
    }

    // ------------------------------------------------------------------------------------------

    Cells flatten(Component component) {
        Cells cells = new Cells();
        flatten(component, Style.empty(), cells);
        return cells;
    }

    private void flatten(Component component, Style parent, Cells out) {
        Style style = parent.merge(component.style());
        String content;
        if (component instanceof TextComponent text) {
            content = text.content();
        } else if (component instanceof ObjectComponent) {
            content = "";
        } else {
            content = PlainTextComponentSerializer.plainText().serialize(component.children(List.of()));
        }
        if (!content.isEmpty()) {
            Style clean = clean(style);
            boolean bold = clean.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
            content.codePoints().forEach(cp -> addSanitized(out, cp, clean, bold));
        }
        for (Component child : component.children()) {
            flatten(child, style, out);
        }
    }

    private void addSanitized(Cells out, int cp, Style style, boolean bold) {
        if (cp == '\r') {
            return;
        }
        if (cp == '\n') {
            out.add(cp, style, 0);
            return;
        }
        if (cp == '\t' || Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            cp = ' ';
        }
        if (!widths.has(cp)) {
            if (replacement < 0) {
                return;
            }
            cp = replacement;
        }
        out.add(cp, style, widths.advance(cp, bold));
    }

    private static Style clean(Style style) {
        Style.Builder builder = Style.style();
        if (style.color() != null) {
            builder.color(style.color());
        }
        for (TextDecoration decoration : KEPT_DECORATIONS) {
            if (style.decoration(decoration) == TextDecoration.State.TRUE) {
                builder.decoration(decoration, true);
            }
        }
        if (style.shadowColor() != null) {
            builder.shadowColor(style.shadowColor());
        }
        return builder.build();
    }

    static final class Cells {
        final List<Integer> codepoints = new ArrayList<>();
        final List<Style> styles = new ArrayList<>();
        final List<Integer> advances = new ArrayList<>();

        void add(int codepoint, Style style, int advance) {
            codepoints.add(codepoint);
            styles.add(style);
            advances.add(advance);
        }

        int size() {
            return codepoints.size();
        }

        int width(int from, int to) {
            int width = 0;
            for (int i = from; i < to; i++) {
                width += advances.get(i);
            }
            return width;
        }
    }

    private static final class LineBuffer {
        private final Cells source;
        private final List<Integer> indices = new ArrayList<>();
        int width;

        LineBuffer(Cells source) {
            this.source = source;
        }

        boolean isEmpty() {
            return indices.isEmpty();
        }

        void add(int from, int to) {
            for (int i = from; i < to; i++) {
                indices.add(i);
                width += source.advances.get(i);
            }
        }

        LineBuffer addAll() {
            add(0, source.size());
            return this;
        }

        StyledLine build() {
            int[] codepoints = new int[indices.size()];
            Style[] styles = new Style[indices.size()];
            int[] advances = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) {
                int index = indices.get(i);
                codepoints[i] = source.codepoints.get(index);
                styles[i] = source.styles.get(index);
                advances[i] = source.advances.get(index);
            }
            return new StyledLine(codepoints, styles, advances);
        }
    }
}
