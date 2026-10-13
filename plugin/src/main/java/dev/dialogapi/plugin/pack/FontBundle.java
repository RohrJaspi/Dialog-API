package dev.dialogapi.plugin.pack;

import java.util.List;
import java.util.Map;

/**
 * The glyph source for the dialog text fonts.
 *
 * @param sheets bitmap providers in priority order (first one wins for duplicate characters)
 * @param spaces characters of {@code space} providers and their advance (e.g. ' ' = 4)
 * @param origin human readable description for logs
 */
public record FontBundle(List<FontSheet> sheets, Map<Integer, Integer> spaces, String origin) {
    public FontBundle {
        sheets = List.copyOf(sheets);
        spaces = Map.copyOf(spaces);
    }
}
