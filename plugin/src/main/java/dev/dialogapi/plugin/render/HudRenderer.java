package dev.dialogapi.plugin.render;

import dev.dialogapi.api.glyph.Glyph;
import dev.dialogapi.api.glyph.GlyphCanvas;
import dev.dialogapi.plugin.config.HudLayout;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

/**
 * Turns a {@link HudFrame} into the action bar component.
 *
 * <p>Vertical placement is baked into the fonts of the pack (one font per line, each with its own
 * ascent); horizontal placement is done with offset characters by {@link GlyphCanvas}.
 * Draw order matters because later glyphs are drawn on top: boxes first, then text, then icons.
 */
public final class HudRenderer {

    /** GlyphInfo.simple(8) on the client: the player head object is 8 pixels wide. */
    private static final int HEAD_ADVANCE = 8;
    private static final Style OBJECT_STYLE = Style.style()
            .color(NamedTextColor.WHITE)
            .shadowColor(ShadowColor.none())
            .build();

    private final HudAssets assets;
    private final HudLayout layout;
    private final Style textStyle;
    private final int origin;

    public HudRenderer(HudAssets assets, TextColor textColor, boolean textShadow) {
        this.assets = assets;
        this.layout = assets.layout();
        Style.Builder style = Style.style().color(textColor);
        if (!textShadow) {
            style.shadowColor(ShadowColor.none());
        }
        this.textStyle = style.build();
        this.origin = -(assets.minX() + assets.maxX()) / 2 + layout.offsetX();
    }

    public HudAssets assets() {
        return assets;
    }

    public Component render(HudFrame frame) {
        GlyphCanvas canvas = new GlyphCanvas(origin);
        boolean portrait = layout.portraitEnabled() && frame.head() != null;

        if (portrait) {
            canvas.glyph(assets.frame(), layout.frameX());
        }
        canvas.glyph(assets.box(), layout.boxX());
        if (frame.name() != null && frame.name().length() > 0) {
            drawNameTab(canvas, frame.name());
        }

        int remaining = frame.visible();
        List<StyledLine> lines = frame.page().lines();
        for (int i = 0; i < lines.size() && i < layout.maxLines() && remaining > 0; i++) {
            StyledLine line = lines.get(i);
            text(canvas, assets.lineFont(i + 1), line, Math.min(line.length(), remaining), layout.textX());
            remaining -= line.length();
        }

        if (frame.indicator()) {
            canvas.glyph(assets.indicator(), layout.indicatorX());
        }

        int rows = Math.min(frame.answers().size(), layout.maxAnswers());
        if (rows > 0) {
            canvas.glyph(assets.answerBox(rows), layout.answerBoxX());
            for (int i = 0; i < rows; i++) {
                int slot = rows - 1 - i;
                int font = layout.maxAnswers() - rows + 1 + i;
                if (i == frame.selected()) {
                    canvas.glyph(assets.pointer(slot), layout.pointerX());
                }
                StyledLine answer = frame.answers().get(i);
                text(canvas, assets.answerFont(font), answer, answer.length(), layout.answerTextX());
            }
        }

        if (portrait) {
            head(canvas, frame.head(), layout.headX());
        }
        return canvas.build();
    }

    /** Draws the first {@code count} characters of a line in {@code font} starting at {@code x}. */
    private void text(GlyphCanvas canvas, Key font, StyledLine line, int count, int x) {
        if (count <= 0) {
            return;
        }
        canvas.styledWithOffset(offset -> line.toComponent(font, textStyle, offset, count), line.width(count), x);
    }

    /** Draws the player head object (always 8x8, on the vanilla action bar line). */
    private static void head(GlyphCanvas canvas, PlayerHeadObjectContents head, int x) {
        canvas.styled(Component.object(head).style(OBJECT_STYLE), HEAD_ADVANCE, x);
    }

    private void drawNameTab(GlyphCanvas canvas, StyledLine name) {
        int left = assets.tabLeft().width();
        int right = assets.tabRight().width();
        int total = Math.max(left + right, layout.namePaddingLeft() + name.width() + layout.namePaddingRight());
        int middle = total - left - right;

        int x = layout.tabX();
        canvas.glyph(assets.tabLeft(), x);
        x += left;
        List<Glyph> pieces = assets.tabMiddle();
        for (int power = pieces.size() - 1; power >= 0 && middle > 0; ) {
            int size = 1 << power;
            if (middle >= size) {
                canvas.glyph(pieces.get(power), x);
                x += size;
                middle -= size;
            } else {
                power--;
            }
        }
        canvas.glyph(assets.tabRight(), x);
        text(canvas, assets.nameFont(), name, name.length(), layout.tabX() + layout.namePaddingLeft());
    }
}
