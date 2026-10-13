package dev.dialogapi.plugin.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Uses {@code plugins/DialogAPI/font/font.json}: a vanilla-style font definition whose
 * {@code file} entries are paths relative to the font folder. If the folder has no font.json,
 * the bundled fallback pixel font is written there first (and can be edited or replaced).
 */
final class CustomFontSource {

    private CustomFontSource() {
    }

    static FontBundle load(Path fontDir) throws IOException {
        Path definition = fontDir.resolve("font.json");
        if (!Files.isRegularFile(definition)) {
            FallbackFont.write(fontDir);
        }
        JsonObject json = JsonParser.parseString(Files.readString(definition, StandardCharsets.UTF_8)).getAsJsonObject();
        return FontDefinitions.parse(json, file -> {
            String relative = file.substring(file.indexOf(':') + 1);
            Path path = fontDir.resolve(relative);
            if (!Files.isRegularFile(path)) {
                path = fontDir.resolve(relative.substring(relative.lastIndexOf('/') + 1));
            }
            return Images.read(path);
        }, "custom font " + definition);
    }
}
