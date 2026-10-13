package dev.dialogapi.api.glyph;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** Where the pixels of a registered glyph come from. */
@FunctionalInterface
public interface ImageSource {

    /** @return the image, or {@code null} if this source has nothing (see {@link #firstOf}) */
    @Nullable BufferedImage load() throws IOException;

    /**
     * A PNG inside the jar of {@code plugin}. The bytes are read right now, so a later pack build
     * never touches a jar that has been closed in the meantime. A missing resource yields a source
     * that returns {@code null}.
     */
    static ImageSource resource(Plugin plugin, String path) {
        byte[] bytes;
        try (InputStream in = plugin.getResource(path)) {
            bytes = in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path + " from " + plugin.getName(), e);
        }
        return bytes == null ? () -> null : bytes(bytes);
    }

    /** A PNG file, read when the pack is built. A missing file yields {@code null}. */
    static ImageSource file(Path file) {
        return () -> Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null;
    }

    static ImageSource bytes(byte[] png) {
        return () -> ImageIO.read(new ByteArrayInputStream(png));
    }

    static ImageSource image(BufferedImage image) {
        return () -> image;
    }

    /** The first source that has an image: an own file, else a shared one. */
    static ImageSource firstOf(ImageSource... sources) {
        return () -> {
            for (ImageSource source : sources) {
                BufferedImage image = source.load();
                if (image != null) {
                    return image;
                }
            }
            return null;
        };
    }
}
