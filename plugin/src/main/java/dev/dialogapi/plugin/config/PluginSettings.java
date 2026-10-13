package dev.dialogapi.plugin.config;

import dev.dialogapi.api.actionbar.AdvanceMode;
import dev.dialogapi.api.actionbar.Pacing;
import dev.dialogapi.api.actionbar.SoundSpec;
import dev.dialogapi.api.actionbar.TypewriterMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/** Values from config.yml. Immutable; a new instance is created on every reload. */
public record PluginSettings(
        String fontSource,
        int replacementChar,
        TextColor textColor,
        boolean textShadow,
        Pacing defaultPacing,
        boolean allowSkip,
        String answerFormat,
        String selectedAnswerFormat,
        boolean wrapSelection,
        boolean numberKeys,
        SoundSpec selectSound,
        SoundSpec confirmSound,
        int indicatorBlinkTicks,
        boolean confirmOnNpcClick,
        boolean confirmOnAnyEntityClick,
        boolean confirmOnSneak,
        boolean confirmOnBlockOrAirClick,
        int inputCooldownTicks,
        double maxDistance,
        int refreshTicks,
        String tagPrefix,
        Map<String, String> messages,
        PackSettings pack) {

    public record PackSettings(String description, int minFormat, int maxFormat,
                               boolean sendOnJoin, boolean required, String prompt, String url,
                               boolean selfHost, String bindAddress, int port, String publicUrl,
                               boolean resendOnChange) {}

    public static PluginSettings load(ConfigurationSection c) {
        Pacing pacing = readPacing(c).orElse(new Pacing(TypewriterMode.LETTER, 1, 4,
                SoundSpec.NONE, AdvanceMode.CLICK, 60));

        String replacement = c.getString("font.replacement-char", "?");
        Map<String, String> messages = new HashMap<>();
        ConfigurationSection msg = c.getConfigurationSection("messages");
        if (msg != null) {
            for (String key : msg.getKeys(false)) {
                messages.put(key, msg.getString(key, ""));
            }
        }

        PackSettings pack = new PackSettings(
                c.getString("pack.description", "DialogAPI"),
                c.getInt("pack.min-format", 69),
                c.getInt("pack.max-format", 97),
                c.getBoolean("pack.send-on-join", true),
                c.getBoolean("pack.required", false),
                c.getString("pack.prompt", ""),
                c.getString("pack.url", ""),
                c.getBoolean("pack.self-host.enabled", true),
                c.getString("pack.self-host.bind-address", "0.0.0.0"),
                c.getInt("pack.self-host.port", 8163),
                c.getString("pack.self-host.public-url", ""),
                c.getBoolean("pack.resend-on-change", true));

        return new PluginSettings(
                c.getString("font.source", "vanilla").toLowerCase(Locale.ROOT),
                replacement == null || replacement.isEmpty() ? '?' : replacement.codePointAt(0),
                parseColor(c.getString("text.default-color"), NamedTextColor.WHITE),
                c.getBoolean("text.shadow", true),
                pacing,
                c.getBoolean("typewriter.allow-skip", true),
                c.getString("question.answer-format", "<gray><number>. <answer>"),
                c.getString("question.selected-format", "<yellow><number>. <answer>"),
                c.getBoolean("question.wrap-selection", true),
                c.getBoolean("question.number-keys", true),
                readSound(c.get("question.select-sound"), SoundSpec.NONE),
                readSound(c.get("question.confirm-sound"), SoundSpec.NONE),
                c.getInt("indicator.blink-ticks", 10),
                c.getBoolean("input.confirm-on-npc-click", true),
                c.getBoolean("input.confirm-on-any-entity-click", false),
                c.getBoolean("input.confirm-on-sneak", true),
                c.getBoolean("input.confirm-on-block-or-air-click", false),
                Math.max(0, c.getInt("input.cooldown-ticks", 4)),
                c.getDouble("session.max-distance", 8.0),
                Math.max(1, Math.min(40, c.getInt("session.refresh-ticks", 20))),
                c.getString("binding.tag-prefix", "dialog."),
                Map.copyOf(messages),
                pack);
    }

    public String message(String key) {
        return messages.getOrDefault(key, "");
    }

    /**
     * Reads the pacing keys ({@code typewriter.*}, {@code advance}, {@code auto-delay}) of a section.
     * Keys that are missing stay {@code null} so they can fall back to an outer level.
     */
    public static Pacing readPacing(ConfigurationSection c) {
        TypewriterMode mode = null;
        if (c.isString("typewriter.mode")) {
            mode = TypewriterMode.valueOf(c.getString("typewriter.mode", "letter").toUpperCase(Locale.ROOT));
        }
        AdvanceMode advance = null;
        if (c.isString("advance")) {
            advance = AdvanceMode.valueOf(c.getString("advance", "click").toUpperCase(Locale.ROOT));
        }
        return new Pacing(
                mode,
                c.isInt("typewriter.speed") ? Math.max(0, c.getInt("typewriter.speed")) : null,
                c.isInt("typewriter.punctuation-delay") ? Math.max(0, c.getInt("typewriter.punctuation-delay")) : null,
                c.contains("typewriter.sound") ? readSound(c.get("typewriter.sound"), SoundSpec.NONE) : null,
                advance,
                c.isInt("auto-delay") ? Math.max(0, c.getInt("auto-delay")) : null);
    }

    /**
     * Reads a sound. Accepts {@code false}/{@code none}, a plain key string, or a section with
     * {@code key, volume, pitch, pitch-variance, every (word|letter), source}.
     */
    public static SoundSpec readSound(@Nullable Object value, SoundSpec fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool ? fallback : SoundSpec.NONE;
        }
        if (value instanceof String key) {
            return key.isBlank() || key.equalsIgnoreCase("none") ? SoundSpec.NONE : SoundSpec.of(key, 1f, 1f);
        }
        if (value instanceof ConfigurationSection s) {
            if (!s.getBoolean("enabled", true)) {
                return SoundSpec.NONE;
            }
            String key = s.getString("key", "");
            if (key.isBlank() || key.equalsIgnoreCase("none")) {
                return SoundSpec.NONE;
            }
            return new SoundSpec(
                    Key.key(key),
                    Sound.Source.valueOf(s.getString("source", "master").toUpperCase(Locale.ROOT)),
                    (float) s.getDouble("volume", 1.0),
                    (float) s.getDouble("pitch", 1.0),
                    (float) s.getDouble("pitch-variance", 0.0),
                    SoundSpec.Trigger.valueOf(s.getString("every", "word").toUpperCase(Locale.ROOT)));
        }
        return fallback;
    }

    public static TextColor parseColor(@Nullable String value, TextColor fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        TextColor hex = TextColor.fromHexString(value.trim());
        if (hex != null) {
            return hex;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(value.trim().toLowerCase(Locale.ROOT));
        return named != null ? named : fallback;
    }
}
