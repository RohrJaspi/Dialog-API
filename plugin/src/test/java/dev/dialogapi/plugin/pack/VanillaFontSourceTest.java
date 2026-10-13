package dev.dialogapi.plugin.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Downloads the real client jar from Mojang, so it only runs with
 * {@code -Ddialogapi.vanillaVersions=1.21.9,1.21.11,26.1.2,26.2,26.3} (comma separated).
 */
class VanillaFontSourceTest {

    @Test
    void everySupportedVersionHasTheSameFontSheetsAtTheSamePlace(@TempDir Path temp) throws Exception {
        String versions = System.getProperty("dialogapi.vanillaVersions");
        Assumptions.assumeTrue(versions != null && !versions.isBlank(), "network test, set -Ddialogapi.vanillaVersions");
        String reference = null;
        for (String version : versions.split(",")) {
            Path cache = temp.resolve(version);
            FontBundle font = VanillaFontSource.load(cache, version, Logger.getLogger("test"));
            assertEquals(3, font.sheets().size(), version + " should have ascii, accented and nonlatin_european");
            assertTrue(font.sheets().stream().anyMatch(sheet -> sheet.name().equals("ascii")), version);
            StringBuilder fingerprint = new StringBuilder();
            font.sheets().forEach(sheet -> fingerprint.append(sheet.name()).append(sheet.ascent()).append('/')
                    .append(sheet.height()).append('/').append(sheet.image().getWidth()).append('x')
                    .append(sheet.image().getHeight()).append(';'));
            if (reference == null) {
                reference = fingerprint.toString();
            }
            assertEquals(reference, fingerprint.toString(), version + " differs from the first version");
        }
    }

    @Test
    void anUnknownVersionFallsBackToTheNewestKnownFont(@TempDir Path temp) throws Exception {
        Assumptions.assumeTrue(System.getProperty("dialogapi.vanillaVersions") != null, "network test");
        FontBundle font = VanillaFontSource.load(temp, "99.9-not-a-version", Logger.getLogger("test"));
        assertEquals(3, font.sheets().size());
    }
}
