package dev.dialogapi.api.actionbar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.jspecify.annotations.Nullable;

/**
 * A sound played by the dialog HUD (typewriter blips, answer selection, ...).
 *
 * @param key           sound event key, {@code null} for "no sound"
 * @param pitchVariance random +/- variation applied to the pitch each time the sound plays
 * @param trigger       for typewriter sounds: play once per word or for every revealed letter
 */
public record SoundSpec(@Nullable Key key, Sound.Source source, float volume, float pitch,
                        float pitchVariance, Trigger trigger) {

    public enum Trigger { WORD, LETTER }

    public static final SoundSpec NONE = new SoundSpec(null, Sound.Source.MASTER, 0f, 1f, 0f, Trigger.WORD);

    public static SoundSpec of(String key, float volume, float pitch) {
        return new SoundSpec(Key.key(key), Sound.Source.MASTER, volume, pitch, 0f, Trigger.WORD);
    }

    public boolean enabled() {
        return key != null && volume > 0f;
    }
}
