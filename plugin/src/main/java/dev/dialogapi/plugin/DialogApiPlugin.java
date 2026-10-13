package dev.dialogapi.plugin;

import dev.dialogapi.api.DialogAPI;
import dev.dialogapi.api.DialogServices;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.DialogAction;
import dev.dialogapi.api.actionbar.DialogNode;
import dev.dialogapi.api.actionbar.EndReason;
import dev.dialogapi.api.actionbar.QuestionNode;
import dev.dialogapi.api.actionbar.TextNode;
import dev.dialogapi.plugin.binding.NpcBindings;
import dev.dialogapi.plugin.command.DialogCommand;
import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.config.PluginSettings;
import dev.dialogapi.plugin.input.InputListener;
import dev.dialogapi.plugin.input.PlayerLifecycleListener;
import dev.dialogapi.plugin.loader.DialogLoader;
import dev.dialogapi.plugin.loader.DialogRegistry;
import dev.dialogapi.plugin.pack.PackBuilder;
import dev.dialogapi.plugin.pack.PackRegistry;
import dev.dialogapi.plugin.pack.PackService;
import dev.dialogapi.plugin.service.DialogServicesImpl;
import dev.dialogapi.plugin.session.SessionManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DialogAPI: action bar dialogs, dialog screens and glyph layouts with one shared resource pack.
 * The plugin owns the pack; other plugins register their assets through the API.
 */
public final class DialogApiPlugin extends JavaPlugin implements Listener {

    private final DialogRegistry registry = new DialogRegistry();
    private final DialogLoader loader = new DialogLoader();
    private PluginSettings settings;
    private HudLayout layout;
    private NpcBindings bindings;
    private SessionManager sessions;
    private PackService packService;
    private DialogServices services;

    @Override
    public void onEnable() {
        saveDefaults();
        bindings = new NpcBindings(this);
        sessions = new SessionManager(this);
        packService = new PackService(this, new PackRegistry());
        packService.onApplied(this::applyBuild);
        loadConfiguration();
        loadDialogs();

        services = new DialogServicesImpl(this);
        DialogAPI.set(services);
        getServer().getServicesManager().register(DialogServices.class, services, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new InputListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new DialogCommand(this).build(),
                        "DialogAPI: action bar dialogs and the shared resource pack", List.of("dapi")));

        // runs after every plugin has enabled and registered its assets
        getServer().getScheduler().runTask(this, () -> rebuildPack());
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.stopAll(EndReason.PLUGIN_DISABLE);
        }
        if (packService != null) {
            packService.shutdown();
        }
        if (services != null) {
            getServer().getServicesManager().unregister(DialogServices.class, services);
        }
        DialogAPI.set(null);
    }

    /** A plugin that went away takes its assets with it (not while the server stops: the pack stays as it is). */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this || Bukkit.isStopping() || packService == null) {
            return;
        }
        packService.registry().remove(PackRegistry.namespaceOf(event.getPlugin().getName()));
    }

    /** Reloads config.yml, layouts, bindings and all dialog files, then rebuilds the pack. */
    public CompletableFuture<PackBuilder.Result> reload() {
        sessions.stopAll(EndReason.RELOAD);
        reloadConfig();
        loadConfiguration();
        loadDialogs();
        return rebuildPack();
    }

    private CompletableFuture<PackBuilder.Result> rebuildPack() {
        return packService.rebuildResult().whenComplete((result, error) -> {
            if (error != null) {
                getLogger().log(Level.SEVERE, "Building the resource pack failed", error);
            }
        });
    }

    /** Runs on the main thread for every applied build. */
    private void applyBuild(PackBuilder.Result result) {
        sessions.setAssets(result.hud(), settings);
    }

    private void saveDefaults() {
        saveDefaultConfig();
        for (String resource : List.of("actionbar/layout.yml", "actionbar/bindings.yml")) {
            if (!new File(getDataFolder(), resource).exists()) {
                saveResource(resource, false);
            }
        }
        if (!new File(getDataFolder(), "actionbar/dialogs").exists()) {
            saveResource("actionbar/dialogs/stats_house.yml", false);
        }
    }

    private void loadConfiguration() {
        settings = PluginSettings.load(getConfig());
        layout = HudLayout.load(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "actionbar/layout.yml")));
        bindings.load(settings.tagPrefix());
        packService.configure(settings, layout);
    }

    private void loadDialogs() {
        Path dir = getDataFolder().toPath().resolve("actionbar/dialogs");
        DialogLoader.Result result = loader.loadAll(dir, layout.maxAnswers());
        for (String problem : result.problems()) {
            getLogger().warning("actionbar/dialogs/" + problem);
        }
        registry.replaceFileDialogs(result.dialogs());
        for (Dialog dialog : registry.all()) {
            prefetchPortraits(dialog);
            for (DialogNode node : dialog.nodes()) {
                checkDialogTargets(dialog, node);
            }
        }
        getLogger().info("Loaded " + result.dialogs().size() + " dialog file(s).");
    }

    /** Jumps to other dialogs can only be checked once every dialog is loaded. */
    private void checkDialogTargets(Dialog dialog, DialogNode node) {
        List<DialogAction> actions = switch (node) {
            case TextNode text -> text.then();
            case QuestionNode question -> question.answers().stream().flatMap(answer -> answer.actions().stream()).toList();
        };
        for (DialogAction action : actions) {
            if (action instanceof DialogAction.StartDialog(String target, String nodeId)) {
                Dialog other = registry.get(target);
                if (other == null || (nodeId != null && other.node(nodeId) == null)) {
                    getLogger().warning("actionbar/dialogs: '" + dialog.id() + "' node '" + node.id()
                            + "' switches to unknown dialog '" + target + (nodeId != null ? "#" + nodeId : "") + "'");
                }
            }
        }
    }

    /** Starts looking up the skins of named portraits so the first conversation already has them. */
    public void prefetchPortraits(Dialog dialog) {
        sessions.portraits().prefetch(dialog.portrait());
        for (DialogNode node : dialog.nodes()) {
            sessions.portraits().prefetch(node.portrait());
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    public HudLayout layout() {
        return layout;
    }

    public DialogRegistry registry() {
        return registry;
    }

    public NpcBindings bindings() {
        return bindings;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public PackService packService() {
        return packService;
    }
}
