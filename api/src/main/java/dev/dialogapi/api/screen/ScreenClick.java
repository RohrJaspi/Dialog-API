package dev.dialogapi.api.screen;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** What the player did: who clicked and the current values of the inputs of the screen. */
public interface ScreenClick {

    Player player();

    /** @return the text of a {@link ScreenInput#text} (or the selected id of {@link ScreenInput#options}) */
    @Nullable String text(String key);

    @Nullable Boolean bool(String key);

    /** @return the value of a {@link ScreenInput#slider} */
    @Nullable Float number(String key);
}
