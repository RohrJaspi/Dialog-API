package dev.dialogapi.plugin.pack;

import dev.dialogapi.api.glyph.GlyphCanvas;
import dev.dialogapi.api.glyph.ImageSource;
import dev.dialogapi.api.glyph.PackAssets;
import dev.dialogapi.api.glyph.Placed;
import dev.dialogapi.api.glyph.TextSlot;
import dev.dialogapi.api.layout.HAlign;
import dev.dialogapi.api.layout.Rect;
import dev.dialogapi.api.layout.VAlign;
import dev.dialogapi.api.screen.ScreenBody;
import java.awt.image.BufferedImage;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * A card made only with the public API and the layout helpers: three stretched boxes (one wider than 256
 * px), a rim tinted at runtime, a 3x scaled icon and text in two sizes. It is the test fixture for
 * everything the pack builder has to get right, and the example in the README: a plugin author builds an
 * item card, a profile or a shop entry the same way.
 *
 * <pre>
 *   +--------+ +--------------------+
 *   |  icon  | | title (2x)         |
 *   |  96px  | | subtitle           |
 *   +--------+ +--------------------+
 *   +--------------------------------+
 *   | label                    value |
 *   +--------------------------------+
 * </pre>
 *
 * Every position comes from splitting one {@link Rect}; there is not a single hand-added coordinate.
 */
final class DemoCard {

    static final String NAMESPACE = "demo";
    static final Rect CARD = Rect.centered(328, 168);

    record Handles(Placed panel, Placed rim, Placed name, Placed wide, Placed icon,
                   TextSlot title, TextSlot subtitle, TextSlot label, TextSlot value) {}

    private DemoCard() {
    }

    static Handles register(PackAssets assets) {
        ImageSource box = ImageSource.image(box(false));

        List<Rect> rows = CARD.rows(8, 112, 48);
        List<Rect> top = rows.get(0).columns(8, 112, Rect.FILL);
        Rect panel = top.get(0);
        Rect nameBox = top.get(1).rows(0, 44, Rect.FILL).get(0);
        Rect wide = rows.get(1);

        List<Rect> nameText = nameBox.inset(8, 4).rows(2, 2 * PackAssets.TEXT_HEIGHT, PackAssets.TEXT_HEIGHT);
        List<Rect> wideText = wide.inset(8, 6).rows(2, PackAssets.TEXT_HEIGHT, PackAssets.TEXT_HEIGHT);

        return new Handles(
                assets.box("panel", box, 4, panel),
                assets.box("panel_rim", ImageSource.image(box(true)), 4, panel),
                assets.box("name", box, 4, nameBox),
                assets.box("wide", box, 4, wide),
                // a 32x32 icon scaled 3x = 96 px, centred in the panel in both directions
                assets.image("icon", ImageSource.image(icon()), 3, panel, HAlign.CENTER, VAlign.MIDDLE),
                assets.text(nameText.get(0), 2, HAlign.LEFT, VAlign.TOP),
                assets.text(nameText.get(1), 1, HAlign.LEFT, VAlign.TOP),
                assets.text(wideText.get(0), 1, HAlign.LEFT, VAlign.TOP),
                assets.text(wideText.get(1), 1, HAlign.RIGHT, VAlign.TOP));
    }

    /** The first line of the body; its total advance is 0. */
    static Component line(Handles card, String title, String subtitle, String label, String value, TextColor accent) {
        return new GlyphCanvas()
                .glyph(card.panel())
                .glyph(card.rim(), accent)
                .glyph(card.name())
                .glyph(card.wide())
                .glyph(card.icon())
                .text(title, card.title(), accent)
                .text(subtitle, card.subtitle(), NamedTextColor.GRAY)
                .text(label, card.label(), NamedTextColor.GRAY)
                .text(value, card.value(), NamedTextColor.WHITE)
                .build();
    }

    /** The line plus the empty lines that keep the dialog buttons below the drawing. */
    static ScreenBody body(Handles card, String title, String subtitle, String label, String value, TextColor accent) {
        return ScreenBody.canvas(line(card, title, subtitle, label, value, accent), CARD.width() + 32, CARD.height());
    }

    /** 16x16: dark translucent fill with a 1 px edge; the rim variant is only the white edge, tinted at runtime. */
    private static BufferedImage box(boolean rim) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                image.setRGB(x, y, rim ? (edge ? 0xFFFFFFFF : 0) : (edge ? 0xFF05080A : 0xD8101714));
            }
        }
        return image;
    }

    private static BufferedImage icon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 24; y++) {
            for (int x = 4; x < 28; x++) {
                double dx = (x - 16) / 12.0;
                double dy = (y - 16) / 8.0;
                if (dx * dx + dy * dy <= 1) {
                    image.setRGB(x, y, y < 16 ? 0xFFC070F0 : 0xFFE090FF);
                }
            }
        }
        return image;
    }
}
