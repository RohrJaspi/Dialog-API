package dev.dialogapi.plugin.loader;

import dev.dialogapi.plugin.config.PluginSettings;
import dev.dialogapi.api.actionbar.Answer;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.DialogAction;
import dev.dialogapi.api.actionbar.DialogNode;
import dev.dialogapi.api.actionbar.Pacing;
import dev.dialogapi.api.actionbar.Portrait;
import dev.dialogapi.api.actionbar.QuestionNode;
import dev.dialogapi.api.actionbar.TextNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Reads dialogs from {@code plugins/DialogAPI/actionbar/dialogs/**.yml}. See the README for the format;
 * {@code dialogs/stats_house.yml} is a complete example.
 */
public final class DialogLoader {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+");

    public record Result(Map<String, Dialog> dialogs, List<String> problems) {}

    /** Loads every .yml file below {@code dir}. Broken files are skipped and reported. */
    public Result loadAll(Path dir, int maxAnswers) {
        Map<String, Dialog> dialogs = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            problems.add("Could not list " + dir + ": " + e.getMessage());
            return new Result(dialogs, problems);
        }
        for (Path path : files) {
            String fileName = dir.relativize(path).toString();
            try {
                Dialog dialog = load(path.toFile());
                if (dialogs.containsKey(dialog.id())) {
                    problems.add(fileName + ": dialog id '" + dialog.id() + "' is already used by another file");
                    continue;
                }
                dialogs.put(dialog.id(), dialog);
                for (DialogNode node : dialog.nodes()) {
                    if (node instanceof QuestionNode question && question.answers().size() > maxAnswers) {
                        problems.add(fileName + ": question '" + node.id() + "' has " + question.answers().size()
                                + " answers, only the first " + maxAnswers + " are shown (answers.max-answers in layout.yml)");
                    }
                }
            } catch (DialogLoadException | IOException | InvalidConfigurationException e) {
                problems.add(fileName + ": " + e.getMessage());
            }
        }
        return new Result(dialogs, problems);
    }

    public Dialog load(File file) throws IOException, InvalidConfigurationException, DialogLoadException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        String defaultId = file.getName().replaceFirst("(?i)\\.yml$", "").toLowerCase(Locale.ROOT);
        return read(yaml, defaultId);
    }

    public Dialog read(ConfigurationSection yaml, String defaultId) throws DialogLoadException {
        String id = yaml.getString("id", defaultId);
        if (!ID.matcher(id).matches()) {
            throw new DialogLoadException("invalid dialog id '" + id + "' (use a-z, 0-9, _ . -)");
        }
        List<DialogNode> nodes = new ArrayList<>();
        if (yaml.isConfigurationSection("nodes")) {
            ConfigurationSection section = yaml.getConfigurationSection("nodes");
            for (String nodeId : section.getKeys(false)) {
                ConfigurationSection nodeSection = section.getConfigurationSection(nodeId);
                if (nodeSection == null) {
                    throw new DialogLoadException("node '" + nodeId + "' must be a section");
                }
                nodes.add(readNode(nodeId, nodeSection));
            }
        } else if (yaml.isList("nodes")) {
            List<Map<?, ?>> list = yaml.getMapList("nodes");
            for (int i = 0; i < list.size(); i++) {
                ConfigurationSection nodeSection = toSection(list.get(i));
                nodes.add(readNode(nodeSection.getString("id", "node_" + (i + 1)), nodeSection));
            }
        } else {
            throw new DialogLoadException("missing 'nodes'");
        }

        Dialog dialog;
        try {
            dialog = new Dialog(id,
                    yaml.getString("speaker"),
                    readPortrait(yaml.get("portrait")),
                    readPacing(yaml),
                    yaml.isSet("max-distance") ? yaml.getDouble("max-distance") : null,
                    yaml.getString("start"),
                    nodes);
        } catch (IllegalArgumentException e) {
            throw new DialogLoadException(e.getMessage());
        }
        List<String> problems = dialog.validate();
        if (!problems.isEmpty()) {
            throw new DialogLoadException(String.join("; ", problems));
        }
        return dialog;
    }

    private DialogNode readNode(String id, ConfigurationSection s) throws DialogLoadException {
        boolean question = s.contains("question") || s.contains("answers");
        String where = (question ? "question '" : "node '") + id + "'";
        List<String> pages = readText(s, question ? "question" : "text");
        if (pages.isEmpty()) {
            throw new DialogLoadException(where + " needs '" + (question ? "question" : "text") + "'");
        }
        String speaker = s.getString("speaker");
        Portrait portrait = readPortrait(s.get("portrait"));
        List<DialogAction> onEnter = readSideEffects(s);

        if (!question) {
            return new TextNode(id, pages, speaker, portrait, readPacing(s), onEnter, readFlow(s, where));
        }
        List<Answer> answers = new ArrayList<>();
        List<Map<?, ?>> answerList = s.getMapList("answers");
        for (int i = 0; i < answerList.size(); i++) {
            ConfigurationSection answer = toSection(answerList.get(i));
            String text = answer.getString("text");
            if (text == null) {
                throw new DialogLoadException(where + " answer " + (i + 1) + " needs 'text'");
            }
            List<DialogAction> actions = new ArrayList<>(readSideEffects(answer));
            actions.addAll(readFlow(answer, where + " answer " + (i + 1)));
            answers.add(new Answer(text, actions));
        }
        if (answers.isEmpty()) {
            throw new DialogLoadException(where + " needs at least one entry in 'answers'");
        }
        int timeout = Math.max(0, s.getInt("timeout", 0));
        int defaultAnswer = s.getInt("default-answer", 0) - 1;
        return new QuestionNode(id, pages, speaker, portrait, readPacing(s), onEnter, answers, timeout, defaultAnswer);
    }

    private static Pacing readPacing(ConfigurationSection s) throws DialogLoadException {
        try {
            return PluginSettings.readPacing(s);
        } catch (IllegalArgumentException e) {
            throw new DialogLoadException("invalid typewriter/advance setting: " + e.getMessage());
        }
    }

    private static List<String> readText(ConfigurationSection s, String key) {
        if (s.isList(key)) {
            return s.getStringList(key).stream().filter(text -> !text.isBlank()).toList();
        }
        String text = s.getString(key);
        return text == null || text.isBlank() ? List.of() : List.of(text);
    }

    private static List<DialogAction> readSideEffects(ConfigurationSection s) {
        List<DialogAction> actions = new ArrayList<>();
        for (String message : strings(s, "message", "messages")) {
            actions.add(DialogAction.message(message));
        }
        for (String command : strings(s, "command", "commands")) {
            actions.add(DialogAction.consoleCommand(command));
        }
        for (String command : strings(s, "player-command", "player-commands")) {
            actions.add(DialogAction.playerCommand(command));
        }
        return actions;
    }

    private static List<DialogAction> readFlow(ConfigurationSection s, String where) throws DialogLoadException {
        int count = (s.getBoolean("end", false) ? 1 : 0) + (s.isSet("goto") ? 1 : 0) + (s.isSet("dialog") ? 1 : 0);
        if (count > 1) {
            throw new DialogLoadException(where + " can only use one of 'goto', 'dialog' and 'end'");
        }
        if (s.getBoolean("end", false)) {
            return List.of(DialogAction.end());
        }
        if (s.isSet("goto")) {
            return List.of(DialogAction.target(s.getString("goto", "")));
        }
        if (s.isSet("dialog")) {
            String target = s.getString("dialog", "");
            int hash = target.indexOf('#');
            return List.of(hash < 0 ? DialogAction.startDialog(target)
                    : DialogAction.startDialog(target.substring(0, hash), target.substring(hash + 1)));
        }
        return List.of();
    }

    private static List<String> strings(ConfigurationSection s, String single, String plural) {
        List<String> values = new ArrayList<>();
        if (s.isString(single)) {
            values.add(s.getString(single));
        }
        if (s.isList(plural)) {
            values.addAll(s.getStringList(plural));
        } else if (s.isString(plural)) {
            values.add(s.getString(plural));
        }
        return values;
    }

    static @Nullable Portrait readPortrait(@Nullable Object value) throws DialogLoadException {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            try {
                return string.isBlank() || string.equalsIgnoreCase("none") ? null : Portrait.parse(string);
            } catch (IllegalArgumentException e) {
                throw new DialogLoadException("invalid portrait: " + e.getMessage());
            }
        }
        if (value instanceof ConfigurationSection s) {
            boolean hat = s.getBoolean("hat", true);
            try {
                Portrait portrait;
                if (s.getBoolean("viewer", false)) {
                    portrait = Portrait.viewer();
                } else if (s.isString("skin-file")) {
                    portrait = Portrait.skinFile(s.getString("skin-file"));
                } else if (s.isString("texture")) {
                    portrait = Portrait.texture(s.getString("texture"), s.getString("signature"));
                } else if (s.isString("uuid")) {
                    portrait = Portrait.uuid(UUID.fromString(s.getString("uuid")));
                } else if (s.isString("name")) {
                    portrait = Portrait.player(s.getString("name"));
                } else {
                    throw new DialogLoadException("portrait needs one of name, uuid, texture, skin-file, viewer");
                }
                return portrait.withHat(hat);
            } catch (IllegalArgumentException e) {
                throw new DialogLoadException("invalid portrait: " + e.getMessage());
            }
        }
        throw new DialogLoadException("invalid portrait '" + value + "'");
    }

    /** Converts a map from a YAML list into a section (nested maps become sections too). */
    private static ConfigurationSection toSection(Map<?, ?> map) {
        return new MemoryConfiguration().createSection("entry", map);
    }
}
