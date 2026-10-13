package dev.dialogapi.plugin.pack;

import com.google.gson.JsonArray;
import dev.dialogapi.api.glyph.Glyph;
import dev.dialogapi.api.glyph.GlyphRef;
import dev.dialogapi.api.glyph.GlyphWidths;
import dev.dialogapi.api.glyph.OffsetChars;
import dev.dialogapi.api.glyph.TextFontRef;
import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.config.PluginSettings.PackSettings;
import dev.dialogapi.plugin.pack.PackRegistry.FileReg;
import dev.dialogapi.plugin.pack.PackRegistry.ImageReg;
import dev.dialogapi.plugin.pack.PackRegistry.NineSliceReg;
import dev.dialogapi.plugin.pack.PackRegistry.TextReg;
import dev.dialogapi.plugin.render.HudAssets;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;

/**
 * Builds the one server resource pack: the action bar HUD, the assets registered through
 * {@link dev.dialogapi.api.glyph.PackAssets} and all text fonts they need.
 *
 * <h2>Output</h2>
 * <ul>
 *   <li>{@code dialogapi:glyphs}: offset characters and every bitmap glyph (private use area)</li>
 *   <li>{@code dialogapi:space}: offset characters only, for plain text that has to be shifted</li>
 *   <li>{@code dialogapi:text/s<scale>_<m|p><top>}: one text font per requested size and height</li>
 * </ul>
 *
 * <p>The HUD is built from {@link HudLayout} and is required: a broken layout fails the build. Assets
 * registered by other plugins are isolated: a broken image or layout is logged, skipped, and the rest
 * of the pack is still built.
 */
public final class PackBuilder {

    public static final String NAMESPACE = TextFontFactory.NAMESPACE;

    /**
     * @param texturesDir   {@code plugins/DialogAPI/textures}: the HUD textures live in {@code actionbar/},
     *                      replacements for registered images in {@code <namespace>/<id>.png}
     * @param codepointFile where the glyph to character assignments are remembered
     */
    public record Input(FontBundle font, int replacement, PackSettings pack, HudLayout layout, Path texturesDir,
                        Path packDir, Path zipFile, Path codepointFile, PackRegistry.Snapshot registrations,
                        Logger logger) {}

    public record Result(HudAssets hud, byte[] zip, String sha1, String fontOrigin,
                         Map<GlyphRef, Glyph> glyphs, Map<dev.dialogapi.api.glyph.TextFontRef, Key> textKeys,
                         Map<Integer, GlyphWidths> widthsByScale, int replacement, List<String> problems) {

        /** Fills the glyph and font handles that were registered for this build (main thread). */
        public void resolveHandles() {
            glyphs.forEach(GlyphRef::resolve);
            textKeys.forEach((ref, key) -> ref.resolve(key, widthsByScale.get(ref.scale()), replacement));
        }
    }

    private final Input in;
    private final Map<String, byte[]> files = new TreeMap<>();
    private final List<String> problems = new ArrayList<>();

    public PackBuilder(Input input) {
        this.in = input;
    }

    public Result build() throws IOException {
        HudLayout layout = in.layout();
        List<String> layoutProblems = layout.validate();
        if (!layoutProblems.isEmpty()) {
            throw new IOException("Invalid layout.yml: " + String.join("; ", layoutProblems));
        }
        Path hudDir = in.texturesDir().resolve("actionbar");
        PlaceholderTextures.writeMissing(hudDir);

        Map<Integer, Integer> offsets = OffsetChars.advances();

        // ------------------------------------------------------------------ text fonts
        Set<TextFontFactory.Request> requests = new LinkedHashSet<>();
        for (int line = 1; line <= layout.maxLines(); line++) {
            requests.add(new TextFontFactory.Request(layout.lineTop(line - 1), 1));
        }
        requests.add(new TextFontFactory.Request(layout.nameTextY(), 1));
        for (int k = 1; k <= layout.maxAnswers(); k++) {
            requests.add(new TextFontFactory.Request(layout.answerFontTop(k), 1));
        }
        for (TextReg text : in.registrations().texts()) {
            requests.add(new TextFontFactory.Request(text.top(), text.scale()));
        }
        TextFontFactory.Result text = TextFontFactory.write(in.font(), requests, offsets, files);

        // ------------------------------------------------------------------ glyphs
        CodepointStore codepoints = CodepointStore.load(in.codepointFile(), in.logger());
        GlyphAllocator glyphs = new GlyphAllocator(files, codepoints);
        HudAssets hud = buildHud(layout, hudDir, glyphs, text);

        Map<GlyphRef, Glyph> resolved = new LinkedHashMap<>();
        for (ImageReg reg : in.registrations().images()) {
            try {
                BufferedImage image = load(reg.namespace(), reg.id(), reg.source());
                if (reg.scale() > 1) {
                    image = Images.scaleNearest(image, reg.scale());
                }
                int top = reg.top().applyAsInt(image.getHeight());
                resolved.put(reg.ref(), glyphs.add(reg.namespace(), reg.id(), image, top).getFirst());
            } catch (Exception e) {
                problem(reg.namespace(), reg.id(), e);
            }
        }
        for (NineSliceReg reg : in.registrations().nineSlices()) {
            try {
                BufferedImage image = Images.nineSlice(load(reg.namespace(), reg.id(), reg.source()),
                        reg.slice(), reg.width(), reg.height());
                resolved.put(reg.ref(), glyphs.add(reg.namespace(), reg.id(), image, reg.top()).getFirst());
            } catch (Exception e) {
                problem(reg.namespace(), reg.id(), e);
            }
        }

        JsonArray glyphProviders = new JsonArray();
        glyphProviders.add(PackIo.spaceProvider(offsets));
        glyphProviders.addAll(glyphs.providers());
        files.put(PackIo.fontPath(NAMESPACE, "glyphs"), PackIo.json(PackIo.providersObject(glyphProviders)));
        JsonArray spaceProviders = new JsonArray();
        spaceProviders.add(PackIo.spaceProvider(offsets));
        files.put(PackIo.fontPath(NAMESPACE, "space"), PackIo.json(PackIo.providersObject(spaceProviders)));

        // ------------------------------------------------------------------ other files
        for (FileReg file : in.registrations().files()) {
            files.put("assets/" + file.namespace() + "/" + file.path(), file.bytes());
        }
        copyPortraits(hudDir);
        Path icon = in.texturesDir().resolve("pack.png");
        if (Files.isRegularFile(icon)) {
            files.put("pack.png", Files.readAllBytes(icon));
        }
        files.put("pack.mcmeta", PackIo.json(PackIo.packMeta(in.pack().description(),
                in.pack().minFormat(), in.pack().maxFormat())));

        PackIo.writeDirectory(in.packDir(), files);
        byte[] zip = PackIo.zip(files);
        String sha1 = PackIo.sha1(zip);
        Files.createDirectories(in.zipFile().toAbsolutePath().getParent());
        Files.write(in.zipFile(), zip);
        Files.writeString(in.zipFile().resolveSibling(in.zipFile().getFileName() + ".sha1"),
                sha1 + System.lineSeparator());
        codepoints.save(in.codepointFile());

        Map<dev.dialogapi.api.glyph.TextFontRef, Key> textKeys = new LinkedHashMap<>();
        for (TextReg reg : in.registrations().texts()) {
            textKeys.put(reg.ref(), text.keys().get(new TextFontFactory.Request(reg.top(), reg.scale())));
        }
        return new Result(hud, zip, sha1, in.font().origin(), resolved, textKeys, text.widthsByScale(),
                in.replacement(), List.copyOf(problems));
    }

    // ---------------------------------------------------------------------------------- hud

    private HudAssets buildHud(HudLayout layout, Path hudDir, GlyphAllocator glyphs, TextFontFactory.Result text)
            throws IOException {
        Glyph box = hud(glyphs, "dialog_box", texture(hudDir, PlaceholderTextures.DIALOG_BOX), layout.boxY()).getFirst();
        Glyph frame = hud(glyphs, "portrait_frame", texture(hudDir, PlaceholderTextures.PORTRAIT_FRAME),
                layout.frameY()).getFirst();
        Glyph indicator = hud(glyphs, "continue", texture(hudDir, PlaceholderTextures.CONTINUE),
                layout.indicatorY()).getFirst();

        BufferedImage tab = texture(hudDir, PlaceholderTextures.NAME_TAB);
        int left = layout.tabSliceLeft();
        int right = layout.tabSliceRight();
        if (left < 1 || right < 1 || left + right >= tab.getWidth()) {
            throw new IOException("name-tab slice-left + slice-right must be smaller than the width of "
                    + PlaceholderTextures.NAME_TAB + " (" + tab.getWidth() + " px)");
        }
        Glyph tabLeft = hud(glyphs, "name_tab_left", Images.crop(tab, 0, 0, left, tab.getHeight()), layout.tabY()).getFirst();
        Glyph tabRight = hud(glyphs, "name_tab_right",
                Images.crop(tab, tab.getWidth() - right, 0, right, tab.getHeight()), layout.tabY()).getFirst();
        BufferedImage tabMiddleSource = Images.crop(tab, left, 0, tab.getWidth() - left - right, tab.getHeight());
        List<Glyph> tabMiddle = new ArrayList<>();
        for (int power = 0; power < 8; power++) {
            tabMiddle.add(hud(glyphs, "name_tab_middle_" + (1 << power),
                    Images.tileHorizontally(tabMiddleSource, 1 << power), layout.tabY()).getFirst());
        }

        BufferedImage answerSource = texture(hudDir, PlaceholderTextures.ANSWER_BOX);
        List<Glyph> answerBoxes = new ArrayList<>();
        for (int rows = 1; rows <= layout.maxAnswers(); rows++) {
            BufferedImage image = Images.stretchVertically(answerSource, layout.answerSliceTop(),
                    layout.answerSliceBottom(), rows * layout.answerRowHeight());
            answerBoxes.add(hud(glyphs, "answer_box_" + rows, image,
                    layout.answerBoxBottom() - image.getHeight()).getFirst());
        }

        int[] pointerTops = new int[layout.maxAnswers()];
        for (int slot = 0; slot < pointerTops.length; slot++) {
            pointerTops[slot] = layout.answerRowTop(slot) + layout.pointerOffsetY();
        }
        List<Glyph> pointers = hud(glyphs, "pointer", texture(hudDir, PlaceholderTextures.POINTER), pointerTops);

        List<Key> lineFonts = new ArrayList<>();
        for (int line = 1; line <= layout.maxLines(); line++) {
            lineFonts.add(text.keys().get(new TextFontFactory.Request(layout.lineTop(line - 1), 1)));
        }
        List<Key> answerFonts = new ArrayList<>();
        for (int k = 1; k <= layout.maxAnswers(); k++) {
            answerFonts.add(text.keys().get(new TextFontFactory.Request(layout.answerFontTop(k), 1)));
        }
        Key nameFont = text.keys().get(new TextFontFactory.Request(layout.nameTextY(), 1));
        return new HudAssets(layout, text.widthsByScale().get(1), nameFont, lineFonts, answerFonts, box, frame,
                indicator, tabLeft, tabRight, tabMiddle, answerBoxes, pointers);
    }

    private static List<Glyph> hud(GlyphAllocator glyphs, String name, BufferedImage image, int... tops)
            throws IOException {
        return glyphs.add(NAMESPACE, "hud/" + name, image, tops);
    }

    private static BufferedImage texture(Path dir, String name) throws IOException {
        return Images.read(dir.resolve(name));
    }

    // ---------------------------------------------------------------------------------- registered assets

    /** A replacement in {@code textures/<namespace>/<id>.png} wins over the registered source. */
    private BufferedImage load(String namespace, String id, dev.dialogapi.api.glyph.ImageSource source)
            throws IOException {
        Path override = in.texturesDir().resolve(namespace).resolve(id + ".png");
        BufferedImage image = Files.isRegularFile(override) ? Images.read(override) : source.load();
        if (image == null) {
            throw new IOException("no image available");
        }
        return Images.toArgb(image);
    }

    private void problem(String namespace, String id, Exception e) {
        String message = namespace + ":" + id + ": " + e.getMessage();
        problems.add(message);
        in.logger().warning("Skipping pack asset " + message);
    }

    private void copyPortraits(Path hudDir) throws IOException {
        Path dir = hudDir.resolve(PlaceholderTextures.PORTRAITS_DIR);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path file : stream) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                files.put("assets/" + NAMESPACE + "/textures/portrait/" + name, Files.readAllBytes(file));
            }
        }
    }
}
