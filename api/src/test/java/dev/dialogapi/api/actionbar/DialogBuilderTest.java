package dev.dialogapi.api.actionbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DialogBuilderTest {

    @Test
    void buildsTheReadmeExample() {
        Dialog dialog = Dialog.builder("stats_house")
                .speaker("MaximDe").portrait("MaximDe")
                .text("Welcome to the Stats House! Here you can look at the leaderboards of Duels and compare your statistics.")
                .question("menu", "What do you want to know?")
                    .answer("How does that work?", "explain")
                    .answer("How do I rank up?", "rankup")
                    .answer("Alright then!", Dialog.END)
                .text("explain", "Every duel you win gives you rating points.").then("menu")
                .text("rankup", "Win duels to climb the ladder!").then("menu")
                .build();

        assertEquals("node_1", dialog.startNodeId());
        QuestionNode menu = assertInstanceOf(QuestionNode.class, dialog.node("menu"));
        assertEquals(3, menu.answers().size());
        assertInstanceOf(DialogAction.End.class, menu.answers().get(2).actions().getFirst());
        assertEquals("MaximDe", dialog.portrait().name());
        assertEquals(dialog.node("menu"), dialog.nodeAfter("node_1"));
    }

    @Test
    void rejectsUnknownTargets() {
        assertThrows(IllegalStateException.class, () -> Dialog.builder("broken")
                .question("Where to?")
                    .answer("Nowhere", "missing")
                .build());
    }

    @Test
    void parsesPortraitStrings() {
        assertEquals(true, Portrait.parse("%player%").showViewer());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5",
                Portrait.parse("069a79f444e94726a5befca90e38aaf5").uuid().toString());
        assertEquals("Notch", Portrait.parse("Notch").name());
    }
}
