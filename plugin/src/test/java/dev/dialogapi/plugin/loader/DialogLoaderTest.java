package dev.dialogapi.plugin.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dialogapi.api.actionbar.AdvanceMode;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.DialogAction;
import dev.dialogapi.api.actionbar.QuestionNode;
import dev.dialogapi.api.actionbar.TextNode;
import dev.dialogapi.api.actionbar.TypewriterMode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DialogLoaderTest {

    @Test
    void loadsTheBundledExample() throws Exception {
        YamlConfiguration yaml;
        try (InputStream in = getClass().getResourceAsStream("/actionbar/dialogs/stats_house.yml")) {
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Dialog dialog = new DialogLoader().read(yaml, "stats_house");

        assertEquals("stats_house", dialog.id());
        assertEquals("welcome", dialog.startNodeId());
        QuestionNode menu = assertInstanceOf(QuestionNode.class, dialog.node("menu"));
        assertEquals(4, menu.answers().size());
        assertEquals(400, menu.timeoutTicks());
        assertEquals(2, menu.defaultAnswer());
        assertEquals(new DialogAction.GoTo("explain"), menu.answers().get(0).actions().getFirst());
        assertInstanceOf(DialogAction.ConsoleCommand.class, menu.answers().get(2).actions().get(0));
        assertEquals(new DialogAction.GoTo("bye"), menu.answers().get(2).actions().get(1));

        TextNode explain = assertInstanceOf(TextNode.class, dialog.node("explain"));
        assertEquals(2, explain.pages().size());
        assertEquals(TypewriterMode.WORD, explain.pacing().mode());
        assertEquals(AdvanceMode.AUTO, dialog.node("bye").pacing().advance());
        assertEquals(10.0, dialog.maxDistance());
        assertTrue(dialog.validate().isEmpty());
    }

    @Test
    void readsListStyleNodes() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                speaker: Guide
                nodes:
                  - text: Hello!
                    typewriter: { mode: word, speed: 2 }
                  - id: ask
                    question: Ready?
                    answers:
                      - { text: Yes, end: true }
                      - { text: Again, goto: node_1 }
                """);
        Dialog dialog = new DialogLoader().read(yaml, "guide");
        assertEquals(TypewriterMode.WORD, dialog.node("node_1").pacing().mode());
        assertEquals(2, dialog.node("node_1").pacing().speed());
        assertInstanceOf(QuestionNode.class, dialog.node("ask"));
    }

    @Test
    void reportsBrokenJumps() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                nodes:
                  start:
                    text: Hi
                    goto: nowhere
                """);
        assertThrows(DialogLoadException.class, () -> new DialogLoader().read(yaml, "broken"));
    }
}
