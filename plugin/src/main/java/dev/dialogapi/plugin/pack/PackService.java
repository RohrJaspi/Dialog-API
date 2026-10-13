package dev.dialogapi.plugin.pack;

import dev.dialogapi.api.pack.ResourcePack;
import dev.dialogapi.api.pack.ResourcePackBuildEvent;
import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.config.PluginSettings;
import dev.dialogapi.plugin.config.PluginSettings.PackSettings;
import dev.dialogapi.plugin.render.HudAssets;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Builds the shared resource pack off the main thread, applies the result (resolves the glyph and
 * font handles, refreshes the HUD), optionally serves the zip over HTTP and sends it to players.
 *
 * <p>Registrations ({@link PackRegistry}) schedule a debounced rebuild. A build starts from an
 * immutable snapshot taken on the main thread. If something is registered while it runs, its result
 * is still applied (the handles of the snapshot get resolved), but the pack counts as not ready until
 * the follow-up build that contains the newer registrations has been applied.
 */
public final class PackService implements ResourcePack {

    /** Stable pack id: sending a new version replaces the old one on the client. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("DialogAPI:resource-pack".getBytes(StandardCharsets.UTF_8));
    public static final String ZIP_NAME = "DialogAPI-pack.zip";
    private static final long DEBOUNCE_TICKS = 20;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PackRegistry registry;
    private final ExecutorService executor;
    private final List<Consumer<PackBuilder.Result>> appliedListeners = new ArrayList<>();
    private final List<Runnable> readyCallbacks = new ArrayList<>();
    private volatile PackBuilder.@Nullable Result current;
    private volatile @Nullable PluginSettings settings;
    private @Nullable HudLayout layout;
    private int appliedVersion = -1;
    private @Nullable BukkitTask debounce;
    private @Nullable PackHost host;
    private boolean stopped;

    public PackService(JavaPlugin plugin, PackRegistry registry) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.registry = registry;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DialogAPI-PackBuilder");
            thread.setDaemon(true);
            return thread;
        });
        registry.onChange(this::scheduleRebuild);
    }

    public PackRegistry registry() {
        return registry;
    }

    /** Called with every applied build, before {@link ResourcePackBuildEvent}. */
    public void onApplied(Consumer<PackBuilder.Result> listener) {
        appliedListeners.add(listener);
    }

    public void configure(PluginSettings settings, HudLayout layout) {
        this.settings = settings;
        this.layout = layout;
    }

    /** Schedules a rebuild one second from now; several calls within that time collapse into one. */
    public void scheduleRebuild() {
        if (stopped || settings == null || layout == null) {
            return;
        }
        if (debounce != null) {
            debounce.cancel();
        }
        debounce = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            debounce = null;
            rebuildResult().whenComplete((result, error) -> {
                if (error != null) {
                    logger.log(Level.SEVERE, "Rebuilding the resource pack failed", error);
                }
            });
        }, DEBOUNCE_TICKS);
    }

    /**
     * Rebuilds the pack now (the first build with {@code font.source: vanilla} downloads the client
     * jar). The future completes on the main thread after the new assets are active.
     */
    @Override
    public CompletableFuture<Void> rebuild() {
        return rebuildResult().thenApply(result -> null);
    }

    public CompletableFuture<PackBuilder.Result> rebuildResult() {
        PluginSettings s = settings;
        HudLayout l = layout;
        if (s == null || l == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("DialogAPI is not configured yet"));
        }
        if (debounce != null) {
            debounce.cancel();
            debounce = null;
        }
        Path data = plugin.getDataFolder().toPath();
        String version = Bukkit.getMinecraftVersion();
        int registryVersion = registry.version();
        PackRegistry.Snapshot snapshot = registry.snapshot();
        return CompletableFuture.supplyAsync(() -> {
            FontBundle font = loadFont(s, data, version);
            try {
                return new PackBuilder(new PackBuilder.Input(font, s.replacementChar(), s.pack(), l,
                        data.resolve("textures"), data.resolve("pack"), data.resolve(ZIP_NAME),
                        data.resolve("codepoints.json"), snapshot, logger)).build();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor).thenApplyAsync(result -> {
            apply(s, result, registryVersion);
            return result;
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private FontBundle loadFont(PluginSettings settings, Path data, String version) {
        if (settings.fontSource().equals("vanilla")) {
            try {
                return VanillaFontSource.load(data.resolve("cache"), version, logger);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not load the vanilla font sheets (" + e.getMessage()
                        + "). Falling back to font/font.json.", e);
            }
        }
        try {
            return CustomFontSource.load(data.resolve("font"));
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private void apply(PluginSettings settings, PackBuilder.Result result, int registryVersion) {
        if (stopped) {
            return;
        }
        PackBuilder.Result previous = current;
        current = result;
        appliedVersion = registryVersion;

        result.resolveHandles();
        updateHost(settings.pack(), result);
        boolean changed = previous == null || !previous.sha1().equals(result.sha1());
        logger.info("Resource pack ready (" + result.fontOrigin() + "): " + ZIP_NAME + ", " + result.zip().length / 1024
                + " KiB, SHA-1 " + result.sha1() + (changed ? "" : " (unchanged)"));
        URI uri = uri();
        if (uri != null) {
            logger.info("Resource pack URL: " + uri);
        }
        if (changed && uri != null && previous != null && isExternallyHosted(settings.pack())) {
            logger.warning("The resource pack changed. Upload the new " + ZIP_NAME + " to " + uri
                    + " and update the SHA-1; glyphs keep their characters, so old packs show wrong icons only for new assets.");
        }

        for (Consumer<PackBuilder.Result> listener : appliedListeners) {
            try {
                listener.accept(result);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Error while applying the new resource pack", e);
            }
        }
        if (registryVersion == registry.version()) {
            List<Runnable> callbacks = new ArrayList<>(readyCallbacks);
            readyCallbacks.clear();
            for (Runnable callback : callbacks) {
                run(callback);
            }
        }
        Bukkit.getPluginManager().callEvent(new ResourcePackBuildEvent(result.sha1(), changed));

        if (changed && uri != null) {
            boolean resend = previous == null ? settings.pack().sendOnJoin() : settings.pack().resendOnChange();
            if (resend) {
                Bukkit.getOnlinePlayers().forEach(this::send);
            }
        }
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Error in a resource pack ready callback", e);
        }
    }

    private static boolean isExternallyHosted(PackSettings pack) {
        return !pack.selfHost();
    }

    private void updateHost(PackSettings pack, PackBuilder.Result result) {
        if (!pack.selfHost()) {
            stopHost();
            return;
        }
        if (host != null && !host.matches(pack.bindAddress(), pack.port())) {
            stopHost();
        }
        if (host == null) {
            try {
                host = new PackHost(pack.bindAddress(), pack.port());
                logger.info("Serving the resource pack on " + pack.bindAddress() + ":" + pack.port());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not start the resource pack web server on port " + pack.port(), e);
                return;
            }
        }
        host.update(result.zip(), result.sha1());
    }

    private void stopHost() {
        if (host != null) {
            host.stop();
            host = null;
        }
    }

    public @Nullable HudAssets assets() {
        PackBuilder.Result result = current;
        return result != null ? result.hud() : null;
    }

    @Override
    public boolean isReady() {
        return current != null && appliedVersion == registry.version();
    }

    @Override
    public void whenReady(Runnable task) {
        if (isReady()) {
            task.run();
        } else {
            readyCallbacks.add(task);
        }
    }

    @Override
    public @Nullable String sha1() {
        PackBuilder.Result result = current;
        return result != null ? result.sha1() : null;
    }

    @Override
    public @Nullable String url() {
        URI uri = uri();
        return uri == null ? null : uri.toString();
    }

    /** Download URL players receive, or {@code null} if neither self-hosting nor pack.url is configured. */
    public @Nullable URI uri() {
        PackBuilder.Result result = current;
        PluginSettings s = settings;
        if (result == null || s == null) {
            return null;
        }
        PackSettings pack = s.pack();
        if (pack.selfHost()) {
            String base = pack.publicUrl().isBlank()
                    ? "http://" + (Bukkit.getIp().isBlank() ? "127.0.0.1" : Bukkit.getIp()) + ":" + pack.port()
                    : pack.publicUrl().replaceAll("/+$", "");
            return URI.create(base + "/dialogapi-" + result.sha1() + ".zip");
        }
        return pack.url().isBlank() ? null : URI.create(pack.url().trim());
    }

    /** Sends the pack to a player. Returns false if no URL is configured or the pack is not built yet. */
    @Override
    public boolean send(Player player) {
        URI uri = uri();
        String sha1 = sha1();
        PluginSettings s = settings;
        if (uri == null || sha1 == null || s == null) {
            return false;
        }
        ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1))
                .required(s.pack().required())
                .replace(false);
        if (!s.pack().prompt().isBlank()) {
            request.prompt(MiniMessage.miniMessage().deserialize(s.pack().prompt()));
        }
        player.sendResourcePacks(request.build());
        return true;
    }

    public boolean sendOnJoin() {
        PluginSettings s = settings;
        return s != null && s.pack().sendOnJoin();
    }

    public void shutdown() {
        stopped = true;
        if (debounce != null) {
            debounce.cancel();
        }
        stopHost();
        executor.shutdownNow();
    }

    @Override
    public String toString() {
        String sha1 = sha1();
        return "PackService[" + (sha1 == null ? "not built" : sha1) + "]";
    }
}
