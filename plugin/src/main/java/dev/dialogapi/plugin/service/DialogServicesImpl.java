package dev.dialogapi.plugin.service;

import dev.dialogapi.api.DialogServices;
import dev.dialogapi.api.actionbar.ActionBarDialogs;
import dev.dialogapi.api.glyph.PackAssets;
import dev.dialogapi.api.pack.ResourcePack;
import dev.dialogapi.api.screen.Screens;
import dev.dialogapi.plugin.DialogApiPlugin;
import dev.dialogapi.plugin.pack.PackRegistry;
import dev.dialogapi.plugin.screen.PaperScreens;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class DialogServicesImpl implements DialogServices {

    private final DialogApiPlugin plugin;
    private final ActionBarDialogs actionBar;
    private final Screens screens;

    public DialogServicesImpl(DialogApiPlugin plugin) {
        this.plugin = plugin;
        this.actionBar = new ActionBarDialogsImpl(plugin);
        this.screens = new PaperScreens(plugin);
    }

    @Override
    public ActionBarDialogs actionBar() {
        return actionBar;
    }

    @Override
    public Screens screens() {
        return screens;
    }

    @Override
    public PackAssets assets(Plugin owner) {
        return plugin.packService().registry().assets(PackRegistry.namespaceOf(owner.getName()));
    }

    @Override
    public ResourcePack pack() {
        return plugin.packService();
    }

    @Override
    public String minecraftVersion() {
        return Bukkit.getMinecraftVersion();
    }
}
