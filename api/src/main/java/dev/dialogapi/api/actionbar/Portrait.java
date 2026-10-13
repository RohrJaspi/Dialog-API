package dev.dialogapi.api.actionbar;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The head shown in the portrait box. It is rendered with a player-head text object component
 * (Minecraft 1.21.9+), so it can come from a player name, a UUID, a skin texture value or a
 * skin PNG shipped in the generated resource pack.
 *
 * @param name             player name (skin resolved by name)
 * @param uuid             player UUID (skin resolved by UUID)
 * @param textureValue     base64 "textures" profile property (as used by custom heads)
 * @param textureSignature optional signature of {@code textureValue}
 * @param skinFile         name of a skin PNG in {@code textures/portraits/} (bundled into the pack)
 * @param showViewer       show the head of the player who is talking to the NPC
 * @param hat              render the hat (second skin) layer
 */
public record Portrait(@Nullable String name, @Nullable UUID uuid, @Nullable String textureValue,
                       @Nullable String textureSignature, @Nullable String skinFile,
                       boolean showViewer, boolean hat) {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");
    private static final Pattern SKIN_FILE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** @throws IllegalArgumentException if {@code name} is not a valid Minecraft player name */
    public static Portrait player(String name) {
        if (!PLAYER_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("'" + name + "' is not a valid player name (1-16 of A-Z a-z 0-9 _)");
        }
        return new Portrait(name, null, null, null, null, false, true);
    }

    public static Portrait uuid(UUID uuid) {
        return new Portrait(null, uuid, null, null, null, false, true);
    }

    public static Portrait texture(String base64Value, @Nullable String signature) {
        return new Portrait(null, null, base64Value, signature, null, false, true);
    }

    public static Portrait skinFile(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        if (!SKIN_FILE_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid skin file name '" + fileName + "' (allowed: a-z 0-9 _ . -)");
        }
        return new Portrait(null, null, null, null, name, false, true);
    }

    public static Portrait viewer() {
        return new Portrait(null, null, null, null, null, true, true);
    }

    /**
     * Parses the short string form used by YAML and the builder: {@code %player%} (the viewer),
     * a UUID, a base64 texture value or a player name.
     *
     * @throws IllegalArgumentException if the value is none of these
     */
    public static Portrait parse(String value) {
        String v = value.trim();
        if (v.equalsIgnoreCase("%player%")) {
            return viewer();
        }
        if (UUID_PATTERN.matcher(v).matches()) {
            String hex = v.replace("-", "");
            return uuid(new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16),
                    Long.parseUnsignedLong(hex.substring(16), 16)));
        }
        if (v.length() > 64 && v.startsWith("ey")) {
            return texture(v, null);
        }
        return player(v);
    }

    public Portrait withHat(boolean hat) {
        return new Portrait(name, uuid, textureValue, textureSignature, skinFile, showViewer, hat);
    }
}
