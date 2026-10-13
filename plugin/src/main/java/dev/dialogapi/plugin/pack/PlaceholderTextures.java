package dev.dialogapi.plugin.pack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generates the default HUD textures into {@code plugins/DialogAPI/textures/actionbar/} (only files that
 * do not exist yet, so replaced textures are never overwritten). Sizes match the default layout.yml.
 */
public final class PlaceholderTextures {

    public static final String DIALOG_BOX = "dialog_box.png";
    public static final String PORTRAIT_FRAME = "portrait_frame.png";
    public static final String NAME_TAB = "name_tab.png";
    public static final String ANSWER_BOX = "answer_box.png";
    public static final String POINTER = "pointer.png";
    public static final String CONTINUE = "continue.png";
    public static final String PORTRAITS_DIR = "portraits";

    private static final int CLEAR = 0x00000000;
    private static final int BORDER = 0xFFF2E6C8;
    private static final int INNER = 0xFF4A4458;
    private static final int FILL = 0xD8141020;
    private static final int TAB_FILL = 0xE8282038;
    private static final int FRAME_FILL = 0xE0080810;
    private static final int INK = 0xFF2A2030;
    private static final int PAPER = 0xFFFFF4D6;

    private PlaceholderTextures() {
    }

    public static void writeMissing(Path dir) throws IOException {
        Files.createDirectories(dir.resolve(PORTRAITS_DIR));
        Map<String, Supplier<BufferedImage>> textures = new LinkedHashMap<>();
        textures.put(DIALOG_BOX, () -> panel(240, 52, FILL));
        textures.put(PORTRAIT_FRAME, () -> panel(20, 20, FRAME_FILL));
        textures.put(NAME_TAB, () -> panel(16, 13, TAB_FILL));
        textures.put(ANSWER_BOX, () -> panel(150, 19, FILL));
        textures.put(POINTER, PlaceholderTextures::pointer);
        textures.put(CONTINUE, PlaceholderTextures::continueIndicator);
        for (Map.Entry<String, Supplier<BufferedImage>> entry : textures.entrySet()) {
            Path file = dir.resolve(entry.getKey());
            if (!Files.exists(file)) {
                Files.write(file, Images.png(entry.getValue().get()));
            }
        }
    }

    /** Rounded panel: light 1 px border, dark 1 px inner line, translucent fill. */
    static BufferedImage panel(int width, int height, int fill) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edgeX = x == 0 || x == width - 1;
                boolean edgeY = y == 0 || y == height - 1;
                int color;
                if (edgeX && edgeY) {
                    color = CLEAR;
                } else if (edgeX || edgeY) {
                    color = BORDER;
                } else if (x == 1 || y == 1 || x == width - 2 || y == height - 2) {
                    color = INNER;
                } else {
                    color = fill;
                }
                image.setRGB(x, y, color);
            }
        }
        return image;
    }

    /** A small hand pointing to the right. */
    static BufferedImage pointer() {
        return pixels(new String[]{
                ".OOOO....",
                "OWWWWOOOO",
                "OWWWWWWWO",
                "OWWWWOOOO",
                "OWWWWWO..",
                "OWWWWWO..",
                ".OOOOO...",
        });
    }

    /** The ▽ continue indicator. */
    static BufferedImage continueIndicator() {
        return pixels(new String[]{
                "WWWWWWW",
                ".W...W.",
                "..W.W..",
                "...W...",
        });
    }

    private static BufferedImage pixels(String[] rows) {
        BufferedImage image = new BufferedImage(rows[0].length(), rows.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                image.setRGB(x, y, switch (rows[y].charAt(x)) {
                    case 'O' -> INK;
                    case 'W' -> PAPER;
                    default -> CLEAR;
                });
            }
        }
        return image;
    }
}
