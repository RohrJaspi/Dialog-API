package dev.dialogapi.plugin.pack;

import dev.dialogapi.api.glyph.GlyphRef;
import dev.dialogapi.api.glyph.ImageSource;
import dev.dialogapi.api.glyph.PackAssets;
import dev.dialogapi.api.glyph.TextFontRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.regex.Pattern;

/**
 * Everything plugins registered for the shared pack. Only touched on the main thread; the pack
 * builder works on an immutable {@link Snapshot}.
 */
public final class PackRegistry {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern FILE = Pattern.compile("[a-z0-9_./-]+");

    public record ImageReg(String namespace, String id, ImageSource source, int scale, IntUnaryOperator top,
                           GlyphRef ref) {}

    public record NineSliceReg(String namespace, String id, ImageSource source, int slice, int width, int height,
                               int top, GlyphRef ref) {}

    public record TextReg(String namespace, int top, int scale, TextFontRef ref) {}

    public record FileReg(String namespace, String path, byte[] bytes) {}

    public record Snapshot(List<ImageReg> images, List<NineSliceReg> nineSlices, List<TextReg> texts,
                           List<FileReg> files) {}

    private final Map<String, Owner> owners = new LinkedHashMap<>();
    private int version;
    private Runnable listener = () -> { };

    /** Called after every change; PackService uses it to schedule a rebuild. */
    public void onChange(Runnable listener) {
        this.listener = listener;
    }

    /** Incremented by every change; compared with the version a build started from. */
    public int version() {
        return version;
    }

    public static String namespaceOf(String pluginName) {
        String ns = pluginName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return ns.isEmpty() ? "plugin" : ns;
    }

    public PackAssets assets(String namespace) {
        if (!ID.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid namespace '" + namespace + "'");
        }
        return owners.computeIfAbsent(namespace, Owner::new);
    }

    /** Drops everything a namespace registered (plugin disabled). */
    public void remove(String namespace) {
        Owner owner = owners.remove(namespace);
        if (owner != null && !owner.isEmpty()) {
            changed();
        }
    }

    public Snapshot snapshot() {
        List<ImageReg> images = new ArrayList<>();
        List<NineSliceReg> nine = new ArrayList<>();
        List<TextReg> texts = new ArrayList<>();
        List<FileReg> files = new ArrayList<>();
        for (Owner owner : owners.values()) {
            images.addAll(owner.images.values());
            nine.addAll(owner.nineSlices.values());
            texts.addAll(owner.texts);
            files.addAll(owner.files.values());
        }
        return new Snapshot(List.copyOf(images), List.copyOf(nine), List.copyOf(texts), List.copyOf(files));
    }

    private void changed() {
        version++;
        listener.run();
    }

    private static void checkId(String id) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("asset id '" + id + "' may only contain a-z, 0-9 and _");
        }
    }

    private final class Owner implements PackAssets {
        private final String namespace;
        private final Map<String, ImageReg> images = new LinkedHashMap<>();
        private final Map<String, NineSliceReg> nineSlices = new LinkedHashMap<>();
        private final List<TextReg> texts = new ArrayList<>();
        private final Map<String, FileReg> files = new LinkedHashMap<>();

        Owner(String namespace) {
            this.namespace = namespace;
        }

        boolean isEmpty() {
            return images.isEmpty() && nineSlices.isEmpty() && texts.isEmpty() && files.isEmpty();
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public GlyphRef image(String id, ImageSource source, int scale, IntUnaryOperator top) {
            checkId(id);
            if (scale < 1 || scale > 16) {
                throw new IllegalArgumentException("scale must be 1-16");
            }
            nineSlices.remove(id);
            GlyphRef ref = new GlyphRef(namespace, id);
            images.put(id, new ImageReg(namespace, id, source, scale, top, ref));
            changed();
            return ref;
        }

        @Override
        public GlyphRef nineSlice(String id, ImageSource source, int slice, int width, int height, int top) {
            checkId(id);
            if (slice < 1 || width < 2 * slice + 1 || height < 2 * slice + 1) {
                throw new IllegalArgumentException("nine-slice " + id + ": slice " + slice
                        + " does not fit into " + width + "x" + height);
            }
            images.remove(id);
            GlyphRef ref = new GlyphRef(namespace, id);
            nineSlices.put(id, new NineSliceReg(namespace, id, source, slice, width, height, top, ref));
            changed();
            return ref;
        }

        @Override
        public TextFontRef text(int top, int scale) {
            if (scale < 1 || scale > 8) {
                throw new IllegalArgumentException("scale must be 1-8");
            }
            TextFontRef ref = new TextFontRef(top, scale);
            texts.add(new TextReg(namespace, top, scale, ref));
            changed();
            return ref;
        }

        @Override
        public void file(String path, byte[] bytes) {
            if (!FILE.matcher(path).matches() || path.contains("..") || path.startsWith("/")) {
                throw new IllegalArgumentException("invalid pack file path '" + path + "'");
            }
            files.put(path, new FileReg(namespace, path, bytes.clone()));
            changed();
        }

        @Override
        public void clear() {
            if (!isEmpty()) {
                images.clear();
                nineSlices.clear();
                texts.clear();
                files.clear();
                changed();
            }
        }
    }
}
