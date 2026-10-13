package dev.dialogapi.api.actionbar;

import org.jspecify.annotations.Nullable;

/**
 * Timing options of a node. Every field is optional: unset values fall back to the dialog's
 * pacing and finally to the defaults from config.yml (see {@link #orElse(Pacing)}).
 *
 * @param mode             letter-by-letter or word-by-word
 * @param speed            ticks between two typewriter steps (0 = instant)
 * @param punctuationDelay extra ticks after {@code . ! ? …} (half of it after {@code , ; :})
 * @param sound            sound played while typing ({@link SoundSpec#NONE} to disable)
 * @param advance          wait for a click or continue automatically
 * @param autoDelay        ticks to wait before continuing automatically
 */
public record Pacing(@Nullable TypewriterMode mode, @Nullable Integer speed, @Nullable Integer punctuationDelay,
                     @Nullable SoundSpec sound, @Nullable AdvanceMode advance, @Nullable Integer autoDelay) {

    public static final Pacing EMPTY = new Pacing(null, null, null, null, null, null);

    /** Returns a copy where every unset value is taken from {@code fallback}. */
    public Pacing orElse(Pacing fallback) {
        return new Pacing(
                mode != null ? mode : fallback.mode,
                speed != null ? speed : fallback.speed,
                punctuationDelay != null ? punctuationDelay : fallback.punctuationDelay,
                sound != null ? sound : fallback.sound,
                advance != null ? advance : fallback.advance,
                autoDelay != null ? autoDelay : fallback.autoDelay);
    }

    public Pacing withMode(TypewriterMode mode) {
        return new Pacing(mode, speed, punctuationDelay, sound, advance, autoDelay);
    }

    public Pacing withSpeed(int speed) {
        return new Pacing(mode, speed, punctuationDelay, sound, advance, autoDelay);
    }

    public Pacing withPunctuationDelay(int punctuationDelay) {
        return new Pacing(mode, speed, punctuationDelay, sound, advance, autoDelay);
    }

    public Pacing withSound(SoundSpec sound) {
        return new Pacing(mode, speed, punctuationDelay, sound, advance, autoDelay);
    }

    public Pacing withAdvance(AdvanceMode advance) {
        return new Pacing(mode, speed, punctuationDelay, sound, advance, autoDelay);
    }

    public Pacing withAutoDelay(int autoDelay) {
        return new Pacing(mode, speed, punctuationDelay, sound, advance, autoDelay);
    }
}
