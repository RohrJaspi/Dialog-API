package dev.dialogapi.plugin.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dialogapi.api.glyph.Glyph;
import dev.dialogapi.api.glyph.GlyphRef;
import dev.dialogapi.api.glyph.ImageSource;
import dev.dialogapi.api.glyph.PackAssets;
import dev.dialogapi.api.glyph.TextFontRef;
import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.render.HudAssets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackBuilderTest {

    private static TestPacks.Built build(Path root) throws IOException {
        return TestPacks.build(root, HudLayout.defaults(), true, registry -> { }, new PackRegistry());
    }

    private static BufferedImage solid(int size, int argb) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    // ------------------------------------------------------------------------------ client rules

    @Test
    void everyProviderObeysTheClientRules() throws IOException {
        TestPacks.Built built = TestPacks.demo();
        int bitmaps = 0;
        for (JsonObject provider : allBitmapProviders(built.packDir())) {
            bitmaps++;
            String resource = provider.get("file").getAsString();
            int ascent = provider.get("ascent").getAsInt();
            int height = provider.get("height").getAsInt();
            assertTrue(ascent <= height, resource + ": ascent " + ascent + " > height " + height);
            int colon = resource.indexOf(':');
            Path texture = built.packDir().resolve("assets/" + resource.substring(0, colon) + "/textures/"
                    + resource.substring(colon + 1));
            BufferedImage image = ImageIO.read(texture.toFile());
            int rows = provider.getAsJsonArray("chars").size();
            String firstRow = provider.getAsJsonArray("chars").get(0).getAsString();
            int columns = firstRow.codePointCount(0, firstRow.length());
            assertTrue(image.getWidth() / columns <= 256 && image.getHeight() / rows <= 256, resource + " has glyph cells above 256 px");
        }
        assertTrue(bitmaps > 0);
    }

    @Test
    void boxesAreStretchedToTheirSizeAndPlacedAtTheirHeight() throws IOException {
        TestPacks.Built built = TestPacks.demo();
        DemoCard.Handles card = built.card();
        Map<String, Integer> ascents = glyphAscents(built.packDir());
        assertEquals(112, card.panel().ref().width());
        assertEquals(112, card.panel().ref().height());
        assertEquals(208, card.name().ref().width());
        assertEquals(44, card.name().ref().height());
        assertEquals(7, ascents.get("demo:panel.png"));
        assertEquals(7, ascents.get("demo:panel_rim.png"));
        assertEquals(7 - 120, ascents.get("demo:wide_0.png"));
    }

    @Test
    void wideBoxesAreSplitIntoSlicesOfAtMost256Pixels() {
        Glyph wide = TestPacks.demo().card().wide().ref().glyph();
        assertEquals(328, wide.width());
        assertEquals(2, wide.slices().size());
        assertEquals(256, wide.slices().get(1).dx());
    }

    @Test
    void theScaledIconIsCentredByItsTopFunction() throws IOException {
        TestPacks.Built built = TestPacks.demo();
        GlyphRef icon = built.card().icon().ref();
        assertEquals(96, icon.width(), "32 px icon scaled 3x");
        assertEquals(96, icon.height());
        assertEquals(7 - 8, glyphAscents(built.packDir()).get("demo:icon.png"), "top = (112 - 96) / 2");
    }

    @Test
    void glyphCodepointsAreDistinctAndNeverInsideTheOffsetCharacters() {
        TestPacks.Built built = TestPacks.demo();
        Set<Integer> codepoints = new HashSet<>();
        for (Glyph glyph : built.result().glyphs().values()) {
            glyph.slices().forEach(slice -> assertTrue(codepoints.add(slice.codepoint()), "duplicate codepoint"));
        }
        hudGlyphs(built.result().hud()).forEach(glyph ->
                glyph.slices().forEach(slice -> assertTrue(codepoints.add(slice.codepoint()), "duplicate HUD codepoint")));
        assertTrue(codepoints.size() > 20, "card + HUD glyphs: " + codepoints.size());
        for (int cp : codepoints) {
            assertTrue(cp >= 0xE000, "codepoint " + Integer.toHexString(cp));
            assertFalse(cp >= 0xE800 && cp <= 0xE83F, "offset characters live here: " + Integer.toHexString(cp));
        }
    }

    // ------------------------------------------------------------------------------ text fonts

    private static List<TextFontRef> cardFonts(DemoCard.Handles card) {
        return List.of(card.title().font(), card.subtitle().font(), card.label().font(), card.value().font());
    }

    @Test
    void thereIsOneTextFontPerRequestWithTheTopOfTheLettersOnTheRow() throws IOException {
        TestPacks.Built built = TestPacks.demo();
        Map<String, Integer> paddingBySheet = new LinkedHashMap<>();
        for (TextFontRef font : cardFonts(built.card())) {
            Path file = built.packDir().resolve("assets/dialogapi/font/" + font.key().value() + ".json");
            assertTrue(Files.isRegularFile(file), "missing font for top " + font.top() + " scale " + font.scale());
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : json.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject();
                if (!provider.get("type").getAsString().equals("bitmap")) {
                    continue;
                }
                FontSheet sheet = sheetFor(provider.get("file").getAsString());
                int height = provider.get("height").getAsInt();
                assertEquals(0, height % font.scale(), "height is a multiple of the scale");
                int padding = height / font.scale() - sheet.height();
                assertTrue(padding >= 0);
                // one padded copy of every sheet is shared by all fonts
                assertEquals(paddingBySheet.computeIfAbsent(sheet.name(), name -> padding), padding, sheet.name());
                assertEquals(7 - font.top() + font.scale() * (sheet.ascent() - 7), provider.get("ascent").getAsInt());
            }
        }
    }

    @Test
    void sheetsArePaddedEnoughForTheHighestHudTextAtScaleOne() throws IOException {
        TestPacks.Built built = TestPacks.demo();
        HudLayout hud = HudLayout.defaults();
        int highest = hud.answerFontTop(hud.maxAnswers());
        Key key = built.result().hud().answerFont(hud.maxAnswers());
        JsonObject json = JsonParser.parseString(Files.readString(
                built.packDir().resolve("assets/dialogapi/font/" + key.value() + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        int checked = 0;
        for (JsonElement element : json.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (provider.get("type").getAsString().equals("bitmap")) {
                FontSheet sheet = sheetFor(provider.get("file").getAsString());
                // at scale 1: ascent = sheetAscent - top, and the client needs ascent <= height
                assertEquals(sheet.ascent() - highest, provider.get("ascent").getAsInt());
                assertTrue(provider.get("height").getAsInt() >= provider.get("ascent").getAsInt());
                checked++;
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    void textWidthsMatchWhatTheClientComputesFromThePack() {
        TestPacks.Built built = TestPacks.demo();
        BodySimulator client = new BodySimulator(built.packDir());
        for (TextFontRef font : cardFonts(built.card())) {
            String text = "Fangbedingung: Regen / Gewitter · Größe 1.203";
            Component component = Component.text(font.sanitize(text), Style.style().font(font.key()).build());
            assertEquals(client.width(component), font.width(text), "top " + font.top() + " scale " + font.scale());
        }
    }

    // ------------------------------------------------------------------------------ determinism, files, overrides

    @Test
    void sameInputGivesTheSameZip(@TempDir Path other) throws IOException {
        TestPacks.Built second = build(other);
        assertEquals(TestPacks.demo().result().sha1(), second.result().sha1());
        assertArrayEquals(TestPacks.demo().result().zip(), second.result().zip());
    }

    @Test
    void aServerOwnerCanReplaceAnImageWithoutTouchingTheOthers(@TempDir Path root) throws IOException {
        build(root);
        Files.createDirectories(root.resolve("textures/demo"));
        Files.write(root.resolve("textures/demo/panel.png"), Images.png(solid(16, 0xFFFF0000)));
        build(root);

        BufferedImage panel = ImageIO.read(root.resolve("pack/assets/demo/textures/font/glyph/panel.png").toFile());
        BufferedImage name = ImageIO.read(root.resolve("pack/assets/demo/textures/font/glyph/name.png").toFile());
        assertEquals(0xFFFF0000, panel.getRGB(50, 50), "the override is applied before nine-slicing");
        assertNotEquals(0xFFFF0000, name.getRGB(50, 20));
    }

    @Test
    void anOverrideChangesThePack(@TempDir Path root) throws IOException {
        String before = build(root).result().sha1();
        Files.createDirectories(root.resolve("textures/demo"));
        BufferedImage custom = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        custom.setRGB(3, 3, 0xFFFF0000);
        Files.write(root.resolve("textures/demo/icon.png"), Images.png(custom));
        assertNotEquals(before, build(root).result().sha1());
    }

    @Test
    void zipContainsTheFontsAndTheMetadata() throws IOException {
        TestPacks.Built built = TestPacks.demo();
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(built.result().zip()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertTrue(names.contains("pack.mcmeta"));
        assertTrue(names.contains("assets/dialogapi/font/glyphs.json"));
        assertTrue(names.contains("assets/dialogapi/font/space.json"));
        assertTrue(names.contains("assets/dialogapi/font/" + built.card().title().font().key().value() + ".json"));
        assertTrue(names.contains("assets/demo/textures/font/glyph/panel.png"));
        assertTrue(names.contains("assets/demo/textures/font/glyph/icon.png"));
        assertTrue(names.contains("assets/dialogapi/textures/font/glyph/hud/dialog_box.png"));
    }

    @Test
    void rawFilesOfAPluginEndUpInItsNamespace() throws IOException {
        TestPacks.Built built = TestPacks.build(HudLayout.defaults(), false, registry ->
                registry.assets("soundplugin").file("sounds.json", "{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("{}", Files.readString(built.packDir().resolve("assets/soundplugin/sounds.json")));
    }

    // ------------------------------------------------------------------------------ stable codepoints

    @Test
    void codepointsStayTheSameWhenAssetsAreAddedOrRemoved(@TempDir Path root) throws IOException {
        PackRegistry registry = new PackRegistry();
        BufferedImage dot = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        dot.setRGB(3, 3, 0xFFFFFFFF);
        GlyphRef a = registry.assets("firstplugin").image("aaa", ImageSource.image(dot), 0);
        GlyphRef b = registry.assets("otherplugin").image("bbb", ImageSource.image(dot), 0);
        TestPacks.Built first = TestPacks.build(root, HudLayout.defaults(), false, r -> { }, registry);
        int codeA = a.glyph().slices().getFirst().codepoint();
        int codeB = b.glyph().slices().getFirst().codepoint();
        int hudBox = first.result().hud().box().slices().getFirst().codepoint();
        assertNotEquals(codeA, codeB);

        // the other plugin disappears, a new one appears: everything that stays keeps its character
        registry.remove("otherplugin");
        GlyphRef c = registry.assets("thirdplugin").image("ccc", ImageSource.image(dot), 0);
        PackBuilder.Result second = new PackBuilder(TestPacks.input(root, HudLayout.defaults(), registry)).build();
        second.resolveHandles();
        assertEquals(codeA, a.glyph().slices().getFirst().codepoint());
        assertEquals(hudBox, second.hud().box().slices().getFirst().codepoint());
        assertNotEquals(codeB, c.glyph().slices().getFirst().codepoint(), "a removed glyph's character is never reused");

        // and it comes back with its old character
        GlyphRef again = registry.assets("otherplugin").image("bbb", ImageSource.image(dot), 0);
        PackBuilder.Result third = new PackBuilder(TestPacks.input(root, HudLayout.defaults(), registry)).build();
        third.resolveHandles();
        assertEquals(codeB, again.glyph().slices().getFirst().codepoint());
    }

    // ------------------------------------------------------------------------------ failures

    @Test
    void aBrokenRegistrationIsSkippedAndTheRestOfThePackIsStillBuilt(@TempDir Path root) throws IOException {
        PackRegistry registry = new PackRegistry();
        PackAssets plugin = registry.assets("brokenplugin");
        GlyphRef missing = plugin.image("missing", () -> null, 0);
        GlyphRef tooHigh = plugin.image("toohigh", ImageSource.image(solid(8, 0xFFFFFFFF)), -300);
        GlyphRef fine = plugin.image("fine", ImageSource.image(solid(8, 0xFFFFFFFF)), 0);
        PackBuilder.Result result = new PackBuilder(TestPacks.input(root, HudLayout.defaults(), registry)).build();
        result.resolveHandles();
        assertEquals(2, result.problems().size(), result.problems().toString());
        assertFalse(missing.isResolved());
        assertFalse(tooHigh.isResolved());
        assertTrue(fine.isResolved());
        assertNotEquals(null, result.hud());
    }

    @Test
    void invalidIdsAndSliceSizesAreRejectedAtRegistration() {
        PackAssets assets = new PackRegistry().assets("test");
        BufferedImage image = solid(16, 0xFFFFFFFF);
        assertThrows(IllegalArgumentException.class, () -> assets.image("Bad Id", ImageSource.image(image), 0));
        assertThrows(IllegalArgumentException.class, () -> assets.nineSlice("box", ImageSource.image(image), 8, 10, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> assets.file("../escape.txt", new byte[0]));
    }

    @Test
    void aSliceLargerThanTheTextureIsSkippedWithAProblem() {
        GlyphRef[] bad = new GlyphRef[1];
        TestPacks.Built built = TestPacks.build(HudLayout.defaults(), false, registry ->
                bad[0] = registry.assets("slices").nineSlice("bad", ImageSource.image(solid(16, 0xFFFFFFFF)), 8, 40, 40, 0));
        assertTrue(built.result().problems().stream().anyMatch(problem -> problem.contains("slices:bad")),
                built.result().problems().toString());
        assertFalse(bad[0].isResolved());
    }

    @Test
    void aBoxTallerThanTheClientGlyphLimitIsSkippedWithAProblem() {
        GlyphRef[] tall = new GlyphRef[1];
        TestPacks.Built built = TestPacks.build(HudLayout.defaults(), false, registry ->
                tall[0] = registry.assets("tallplugin").nineSlice("tall", ImageSource.image(solid(16, 0xFFFFFFFF)), 4, 112, 300, 0));
        assertTrue(built.result().problems().stream().anyMatch(problem -> problem.contains("tallplugin:tall")));
        assertFalse(tall[0].isResolved());
    }

    // ------------------------------------------------------------------------------ helpers

    private static FontSheet sheetFor(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1, file.length() - 4);
        for (FontSheet sheet : TestPacks.font().sheets()) {
            if (sheet.name().equals(name)) {
                return sheet;
            }
        }
        throw new AssertionError("no sheet " + name);
    }

    private static Map<String, Integer> glyphAscents(Path packDir) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(
                packDir.resolve("assets/dialogapi/font/glyphs.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Integer> ascents = new LinkedHashMap<>();
        for (JsonElement element : json.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (provider.get("type").getAsString().equals("bitmap")) {
                String file = provider.get("file").getAsString();
                String namespace = file.substring(0, file.indexOf(':'));
                ascents.put(namespace + ":" + file.substring(file.lastIndexOf('/') + 1), provider.get("ascent").getAsInt());
            }
        }
        return ascents;
    }

    private static List<JsonObject> allBitmapProviders(Path packDir) throws IOException {
        List<JsonObject> providers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packDir.resolve("assets/dialogapi/font"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement element : json.getAsJsonArray("providers")) {
                    if (element.getAsJsonObject().get("type").getAsString().equals("bitmap")) {
                        providers.add(element.getAsJsonObject());
                    }
                }
            }
        }
        return providers;
    }

    private static List<Glyph> hudGlyphs(HudAssets hud) {
        List<Glyph> glyphs = new ArrayList<>(List.of(hud.box(), hud.frame(), hud.indicator(), hud.tabLeft(), hud.tabRight()));
        glyphs.addAll(hud.tabMiddle());
        for (int rows = 1; rows <= hud.layout().maxAnswers(); rows++) {
            glyphs.add(hud.answerBox(rows));
            glyphs.add(hud.pointer(rows - 1));
        }
        return glyphs;
    }
}
