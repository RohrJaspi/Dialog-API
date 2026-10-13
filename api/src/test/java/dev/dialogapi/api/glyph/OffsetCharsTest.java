package dev.dialogapi.api.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OffsetCharsTest {

    @Test
    void encodedCharactersAddUpToTheOffset() {
        Map<Integer, Integer> advances = OffsetChars.advances();
        for (int offset = -5000; offset <= 5000; offset += 7) {
            int sum = OffsetChars.encode(offset).chars().map(advances::get).sum();
            assertEquals(offset, sum, "offset " + offset);
        }
        assertEquals("", OffsetChars.encode(0));
    }
}
