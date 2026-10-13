package dev.dialogapi.api.glyph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Horizontal positioning with a "space" font provider: private-use characters whose advance is
 * +-1, 2, 4 ... 1024 pixels and that draw nothing. Any offset is the sum of a few of them.
 * The characters are defined in every font of the pack, so they can be put in front of any text.
 */
public final class OffsetChars {

    public static final int POSITIVE_BASE = 0xE800;
    public static final int NEGATIVE_BASE = 0xE820;
    private static final int BITS = 11; // 1 .. 1024

    private OffsetChars() {
    }

    /** Character -> advance, for the {@code space} provider of the generated fonts. */
    public static Map<Integer, Integer> advances() {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < BITS; i++) {
            map.put(POSITIVE_BASE + i, 1 << i);
            map.put(NEGATIVE_BASE + i, -(1 << i));
        }
        return map;
    }

    /** The characters that move the cursor by {@code pixels} (may be negative). */
    public static String encode(int pixels) {
        if (pixels == 0) {
            return "";
        }
        int base = pixels > 0 ? POSITIVE_BASE : NEGATIVE_BASE;
        int value = Math.abs(pixels);
        StringBuilder sb = new StringBuilder();
        int max = 1 << (BITS - 1);
        while (value >= max * 2) {
            sb.append((char) (base + BITS - 1));
            value -= max;
        }
        for (int bit = BITS - 1; bit >= 0; bit--) {
            if ((value & (1 << bit)) != 0) {
                sb.append((char) (base + bit));
            }
        }
        return sb.toString();
    }
}
