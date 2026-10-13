package dev.dialogapi.api;

import dev.dialogapi.api.actionbar.ActionBarDialogs;
import dev.dialogapi.api.glyph.PackAssets;
import dev.dialogapi.api.pack.ResourcePack;
import dev.dialogapi.api.screen.Screens;
import org.bukkit.plugin.Plugin;

/** Everything DialogAPI offers. Get it with {@link DialogAPI#get()}. */
public interface DialogServices {

    /** Action bar dialogs (typewriter text, answers, portraits). */
    ActionBarDialogs actionBar();

    /** Dialog screens (the vanilla dialog window). */
    Screens screens();

    /** The assets of {@code plugin}: images and fonts added to the shared resource pack. */
    PackAssets assets(Plugin plugin);

    ResourcePack pack();

    String minecraftVersion();
}
