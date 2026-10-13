package dev.dialogapi.plugin.loader;

import dev.dialogapi.api.actionbar.Dialog;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * All known dialogs. Dialogs registered through the API survive {@code /dialogapi reload}; dialogs
 * from YAML files are replaced on every reload. API dialogs win if both use the same id.
 */
public final class DialogRegistry {

    private final Map<String, Dialog> fileDialogs = new LinkedHashMap<>();
    private final Map<String, Dialog> apiDialogs = new LinkedHashMap<>();

    public @Nullable Dialog get(String id) {
        Dialog dialog = apiDialogs.get(id);
        return dialog != null ? dialog : fileDialogs.get(id);
    }

    public void register(Dialog dialog) {
        apiDialogs.put(dialog.id(), dialog);
    }

    public boolean unregister(String id) {
        return apiDialogs.remove(id) != null;
    }

    public void replaceFileDialogs(Map<String, Dialog> dialogs) {
        fileDialogs.clear();
        fileDialogs.putAll(dialogs);
    }

    public boolean isFromApi(String id) {
        return apiDialogs.containsKey(id);
    }

    public Set<String> ids() {
        Set<String> ids = new TreeSet<>(fileDialogs.keySet());
        ids.addAll(apiDialogs.keySet());
        return ids;
    }

    public Collection<Dialog> all() {
        Map<String, Dialog> all = new LinkedHashMap<>(fileDialogs);
        all.putAll(apiDialogs);
        return all.values();
    }
}
