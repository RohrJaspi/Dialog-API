package dev.dialogapi.plugin.pack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.render.StyledLine;
import dev.dialogapi.plugin.render.TextLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Builds the pack with the default layout and placeholder textures, renders a few HUD states through
 * {@link ActionBarSimulator} and writes previews to {@code build/preview/}.
 *
 * <p>Uses the fallback font. Pass {@code -Dactiondialogs.cacheDir=<dir>} to use (and if needed
 * download) the vanilla font sheets instead.
 */
class HudPreviewTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Path PREVIEW_DIR = Path.of("target", "preview");

    private static PackBuilder.Result result;
    private static Path packDir;
    private static TextLayout text;

    @BeforeAll
    static void buildPack() throws Exception {
        TestPacks.Built built = TestPacks.demo();
        result = built.result();
        packDir = built.packDir();
        text = new TextLayout(result.hud().widths(), '?');
        Files.createDirectories(PREVIEW_DIR);
    }

    @Test
    void packMetaUsesOnlyMinAndMaxFormat() throws IOException {
        JsonObject pack = JsonParser.parseString(Files.readString(packDir.resolve("pack.mcmeta"))).getAsJsonObject()
                .getAsJsonObject("pack");
        assertEquals(69, pack.get("min_format").getAsInt(), "1.21.9");
        assertEquals(97, pack.get("max_format").getAsInt(), "26.3");
        assertFalse(pack.has("pack_format"), "pack_format is only for packs that support formats below 65");
        assertFalse(pack.has("supported_formats"), "supported_formats is not allowed for formats >= 65");
    }

    @Test
    void everyProviderIsValidForTheClient() throws IOException {
        List<Path> fonts;
        try (Stream<Path> walk = Files.walk(packDir.resolve("assets/dialogapi/font"))) {
            fonts = walk.filter(path -> path.toString().endsWith(".json")).toList();
        }
        assertTrue(fonts.size() >= 11, "HUD text fonts, glyphs and space: " + fonts.size());
        for (Path font : fonts) {
            JsonObject json = JsonParser.parseString(Files.readString(font, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : json.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject();
                if (provider.get("type").getAsString().equals("bitmap")) {
                    int ascent = provider.get("ascent").getAsInt();
                    int height = provider.get("height").getAsInt();
                    assertTrue(ascent <= height, font + ": ascent " + ascent + " > height " + height);
                    // the 256 px limit applies to the cells of the texture, not to the GUI height
                    String file = provider.get("file").getAsString();
                    int colon = file.indexOf(':');
                    BufferedImage image = ImageIO.read(packDir.resolve("assets/" + file.substring(0, colon)
                            + "/textures/" + file.substring(colon + 1)).toFile());
                    int rows = provider.getAsJsonArray("chars").size();
                    int columns = provider.getAsJsonArray("chars").get(0).getAsString().codePointCount(
                            0, provider.getAsJsonArray("chars").get(0).getAsString().length());
                    assertTrue(image.getHeight() / rows <= 256 && image.getWidth() / columns <= 256,
                            font + ": cells " + image.getWidth() / columns + "x" + image.getHeight() / rows);
                }
            }
        }
    }

    @Test
    void buildIsDeterministic() {
        String first = TestPacks.build(HudLayout.defaults(), false, registry -> { }).result().sha1();
        String second = TestPacks.build(HudLayout.defaults(), false, registry -> { }).result().sha1();
        assertEquals(first, second);
    }

    @Test
    void renderPreviews() throws IOException {
        ActionBarSimulator client = new ActionBarSimulator(packDir);
        for (Map.Entry<String, Component> scene : PreviewScenes.build(result).entrySet()) {
            assertEquals(0, client.width(scene.getValue()), "total advance must be 0 to keep the HUD centred");
            PreviewScenes.write(client, scene.getValue(), PREVIEW_DIR.resolve(scene.getKey() + ".png"));
        }
    }

    @Test
    void frameStaysSmall() {
        for (Component component : PreviewScenes.build(result).values()) {
            int bytes = GsonComponentSerializer.gson().serialize(component).length();
            assertTrue(bytes < 8192, "action bar JSON is " + bytes + " bytes");
        }
    }

    @Test
    void textFitsTheBox() {
        List<StyledLine> lines = text.wrap(MM.deserialize(
                "<bold>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut "
                        + "labore et dolore magna aliqua. Averyveryveryveryveryveryveryveryveryverylongwordthatdoesnotfit"), 224);
        for (StyledLine line : lines) {
            assertTrue(line.width() <= 224, "line too wide: " + line.plainText() + " = " + line.width());
        }
    }
}
