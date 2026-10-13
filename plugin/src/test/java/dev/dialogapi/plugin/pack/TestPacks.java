package dev.dialogapi.plugin.pack;

import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.config.PluginSettings.PackSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Builds a real pack from the placeholder textures into a temporary folder, with the same pipeline the
 * plugin uses: the HUD, the {@link DemoCard} registered through the public asset API, and anything a
 * test registers.
 *
 * <p>Text uses the fallback pixel font, or the vanilla font sheets if the system property
 * {@code dialogapi.cacheDir} points to a folder containing {@code vanilla-font/}.
 */
public final class TestPacks {

    public static final PackSettings SETTINGS = new PackSettings("test", 69, 97, false, false, "", "", false, "", 0, "", false);
    public static final String MINECRAFT_VERSION = "1.21.11";

    /** @param card the demo card handles, resolved by the build (null if it was not registered) */
    record Built(Path root, PackBuilder.Result result, PackRegistry registry, DemoCard.Handles card) {

        Path packDir() {
            return root.resolve("pack");
        }
    }

    private static Built demo;
    private static FontBundle font;
    private static final List<Path> CREATED = new ArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (CREATED) {
                for (Path path : CREATED) {
                    try {
                        PackFiles.deleteRecursively(path);
                    } catch (IOException ignored) {
                        // temporary folder, the OS cleans it up eventually
                    }
                }
            }
        }));
    }

    private TestPacks() {
    }

    /** Default HUD layout and the demo card, built once per test run. */
    static synchronized Built demo() {
        if (demo == null) {
            demo = build(HudLayout.defaults(), true, registry -> { });
        }
        return demo;
    }

    /**
     * @param withCard whether to register the {@link DemoCard}
     * @param extra    registers more assets (other plugins) before the build
     */
    static Built build(HudLayout hud, boolean withCard, Consumer<PackRegistry> extra) {
        try {
            return build(temporaryDirectory("dialogapi-test"), hud, withCard, extra, new PackRegistry());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Built build(Path root, HudLayout hud, boolean withCard, Consumer<PackRegistry> extra, PackRegistry registry)
            throws IOException {
        DemoCard.Handles card = withCard ? DemoCard.register(registry.assets(DemoCard.NAMESPACE)) : null;
        extra.accept(registry);
        PackBuilder.Result result = new PackBuilder(input(root, hud, registry)).build();
        result.resolveHandles();
        return new Built(root, result, registry, card);
    }

    static PackBuilder.Input input(Path root, HudLayout hud, PackRegistry registry) {
        return new PackBuilder.Input(font(), '?', SETTINGS, hud, root.resolve("textures"), root.resolve("pack"),
                root.resolve("pack.zip"), root.resolve("codepoints.json"), registry.snapshot(),
                Logger.getLogger("DialogApiTest"));
    }

    static synchronized FontBundle font() {
        if (font == null) {
            try {
                String cache = System.getProperty("dialogapi.cacheDir");
                if (cache != null && !cache.isBlank()) {
                    font = VanillaFontSource.load(Path.of(cache), MINECRAFT_VERSION, Logger.getLogger("DialogApiTest"));
                } else {
                    font = CustomFontSource.load(temporaryDirectory("dialogapi-font"));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return font;
    }

    static Path temporaryDirectory(String prefix) throws IOException {
        Path path = Files.createTempDirectory(prefix);
        synchronized (CREATED) {
            CREATED.add(path);
        }
        return path;
    }
}
