package dev.dialogapi.plugin.render;

import java.util.List;

/** The lines that fit into the dialog box at once. */
public record Page(List<StyledLine> lines) {

    public Page {
        lines = List.copyOf(lines);
    }

    /** Total number of characters on the page (the typewriter's end position). */
    public int length() {
        int length = 0;
        for (StyledLine line : lines) {
            length += line.length();
        }
        return length;
    }

    /** Character at a page-wide index (lines concatenated). */
    public int codepointAt(int index) {
        for (StyledLine line : lines) {
            if (index < line.length()) {
                return line.codepoint(index);
            }
            index -= line.length();
        }
        throw new IndexOutOfBoundsException(index);
    }
}
