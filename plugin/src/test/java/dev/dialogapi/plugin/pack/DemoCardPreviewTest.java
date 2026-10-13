package dev.dialogapi.plugin.pack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

/**
 * Draws the {@link DemoCard} the way the client lays out a dialog body (see {@link BodySimulator}) and
 * writes enlarged PNGs to {@code target/preview/}. The screen is a mock-up, not pixel exact.
 */
class DemoCardPreviewTest {

    private static final Path OUT = Path.of("target", "preview");

    @Test
    void writesPreviewsAtDifferentGuiSizes() throws IOException {
        TestPacks.Built built = TestPacks.demo();
        BodySimulator client = new BodySimulator(built.packDir());
        Files.createDirectories(OUT);
        write(built, client, 640, 360, 3, "card_gui3", NamedTextColor.GOLD);
        write(built, client, 960, 540, 2, "card_gui2", TextColor.color(0x55FF55));
        assertTrue(Files.exists(OUT.resolve("card_gui3.png")));
    }

    private static void write(TestPacks.Built built, BodySimulator client, int width, int height, int zoom, String name,
                              TextColor accent) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF141414);
            }
        }
        Component body = ((dev.dialogapi.api.screen.ScreenBody.Text) DemoCard.body(built.card(), "Excalibur",
                "Legendary sword", "Damage", "12 – 18", accent)).content();
        client.drawLines(body, image, width / 2, 60, true);
        BufferedImage zoomed = new BufferedImage(width * zoom, height * zoom, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < zoomed.getHeight(); y++) {
            for (int x = 0; x < zoomed.getWidth(); x++) {
                zoomed.setRGB(x, y, image.getRGB(x / zoom, y / zoom));
            }
        }
        ImageIO.write(zoomed, "png", OUT.resolve(name + ".png").toFile());
    }
}
