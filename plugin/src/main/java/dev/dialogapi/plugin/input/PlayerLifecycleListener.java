package dev.dialogapi.plugin.input;

import dev.dialogapi.plugin.DialogApiPlugin;
import dev.dialogapi.api.actionbar.EndReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Ends dialogs on quit, death and world change, and sends the resource pack on join. */
public final class PlayerLifecycleListener implements Listener {

    private final DialogApiPlugin plugin;

    public PlayerLifecycleListener(DialogApiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.packService().sendOnJoin()) {
            plugin.packService().send(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().stop(event.getPlayer(), EndReason.QUIT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        plugin.sessions().stop(event.getPlayer(), EndReason.DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.sessions().stop(event.getPlayer(), EndReason.WORLD_CHANGE);
    }
}
