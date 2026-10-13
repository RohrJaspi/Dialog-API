package dev.dialogapi.plugin.render;

import dev.dialogapi.api.glyph.Glyph;
import dev.dialogapi.api.glyph.GlyphWidths;
import dev.dialogapi.plugin.config.HudLayout;
import java.util.List;
import net.kyori.adventure.key.Key;

/**
 * Everything the renderer needs to know about the generated resource pack: font keys, the
 * private-use characters of the HUD glyphs (and their advances) and the text width table.
 * Produced by {@link dev.dialogapi.plugin.pack.PackBuilder}.
 */
public final class HudAssets {

    private final HudLayout layout;
    private final GlyphWidths widths;
    private final Key nameFont;
    private final List<Key> lineFonts;
    private final List<Key> answerFonts;
    private final Glyph box;
    private final Glyph frame;
    private final Glyph indicator;
    private final Glyph tabLeft;
    private final Glyph tabRight;
    private final List<Glyph> tabMiddle;
    private final List<Glyph> answerBoxes;
    private final List<Glyph> pointers;
    private final int minX;
    private final int maxX;

    /**
     * @param lineFonts   text font per line, index 0 = first line
     * @param answerFonts text font per answer position, index k-1 (see {@code HudLayout#answerFontTop})
     * @param tabMiddle   stretched middle pieces of the name tab, index i is 2^i pixels wide
     * @param answerBoxes answer box variants, index n-1 holds n rows
     * @param pointers    pointer glyph per answer row, index = row counted from the bottom
     */
    public HudAssets(HudLayout layout, GlyphWidths widths, Key nameFont, List<Key> lineFonts, List<Key> answerFonts,
                     Glyph box, Glyph frame, Glyph indicator, Glyph tabLeft, Glyph tabRight, List<Glyph> tabMiddle,
                     List<Glyph> answerBoxes, List<Glyph> pointers) {
        this.layout = layout;
        this.widths = widths;
        this.nameFont = nameFont;
        this.lineFonts = List.copyOf(lineFonts);
        this.answerFonts = List.copyOf(answerFonts);
        this.box = box;
        this.frame = frame;
        this.indicator = indicator;
        this.tabLeft = tabLeft;
        this.tabRight = tabRight;
        this.tabMiddle = List.copyOf(tabMiddle);
        this.answerBoxes = List.copyOf(answerBoxes);
        this.pointers = List.copyOf(pointers);

        int min = Math.min(layout.boxX(), layout.answerBoxX());
        int max = Math.max(layout.boxX() + box.width(), layout.answerBoxX() + answerBoxes.getLast().width());
        max = Math.max(max, layout.indicatorX() + indicator.width());
        if (layout.portraitEnabled()) {
            min = Math.min(min, Math.min(layout.frameX(), layout.headX()));
            max = Math.max(max, Math.max(layout.frameX() + frame.width(), layout.headX() + HudLayout.HEAD_SIZE));
        }
        this.minX = Math.min(min, layout.tabX());
        this.maxX = max;
    }

    /** @param line 1-based */
    public Key lineFont(int line) {
        return lineFonts.get(line - 1);
    }

    /** @param k 1-based, as in {@code HudLayout#answerFontTop} */
    public Key answerFont(int k) {
        return answerFonts.get(k - 1);
    }

    public Key nameFont() {
        return nameFont;
    }

    public HudLayout layout() {
        return layout;
    }

    public GlyphWidths widths() {
        return widths;
    }

    public Glyph box() {
        return box;
    }

    public Glyph frame() {
        return frame;
    }

    public Glyph indicator() {
        return indicator;
    }

    public Glyph tabLeft() {
        return tabLeft;
    }

    public Glyph tabRight() {
        return tabRight;
    }

    public List<Glyph> tabMiddle() {
        return tabMiddle;
    }

    public Glyph answerBox(int rows) {
        return answerBoxes.get(rows - 1);
    }

    public Glyph pointer(int slotFromBottom) {
        return pointers.get(slotFromBottom);
    }

    /** Left edge of the HUD in layout coordinates. */
    public int minX() {
        return minX;
    }

    /** Right edge of the HUD in layout coordinates. */
    public int maxX() {
        return maxX;
    }
}
