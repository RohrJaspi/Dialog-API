package dev.dialogapi.plugin.pack;

import dev.dialogapi.plugin.config.HudLayout;
import dev.dialogapi.plugin.render.HudFrame;
import dev.dialogapi.plugin.render.HudRenderer;
import dev.dialogapi.plugin.render.Page;
import dev.dialogapi.plugin.render.StyledLine;
import dev.dialogapi.plugin.render.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

/** Sample HUD states rendered by the preview test and the example pack task. */
final class PreviewScenes {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private PreviewScenes() {
    }

    /** name -> action bar component */
    static Map<String, Component> build(PackBuilder.Result result) {
        HudLayout layout = result.hud().layout();
        TextLayout text = new TextLayout(result.hud().widths(), '?');
        HudRenderer renderer = new HudRenderer(result.hud(), NamedTextColor.WHITE, true);

        StyledLine name = text.singleLine(MM.deserialize("<gold>MaximDe"), layout.nameMaxWidth());
        PlayerHeadObjectContents head = ObjectContents.playerHead("MaximDe");
        Page page = text.paginate(List.of(MM.deserialize(
                "Welcome to the <aqua>Stats House</aqua>! Here you can look at the leaderboards of "
                        + "<yellow>Duels</yellow> and compare your <bold>statistics</bold>.")),
                layout.textWidth(), layout.maxLines()).getFirst();

        Page question = text.paginate(List.of(MM.deserialize("What do you want to know?")),
                layout.textWidth(), layout.maxLines()).getFirst();
        List<StyledLine> answers = new ArrayList<>();
        String[] options = {"How does that work?", "How do I rank up?", "Alright then!"};
        for (int i = 0; i < options.length; i++) {
            String format = i == 1 ? "<#FFE066><number>. <answer>" : "<#B8B8B8><number>. <answer>";
            answers.add(text.singleLine(MM.deserialize(format, Placeholder.unparsed("number", Integer.toString(i + 1)),
                    Placeholder.unparsed("answer", options[i])), layout.answerTextWidth()));
        }

        Map<String, Component> scenes = new LinkedHashMap<>();
        scenes.put("hud_typing", renderer.render(new HudFrame(name, head, page, page.length() / 2, false, List.of(), 0)));
        scenes.put("hud_waiting", renderer.render(new HudFrame(name, head, page, page.length(), true, List.of(), 0)));
        scenes.put("hud_question", renderer.render(new HudFrame(name, head, question, question.length(), false, answers, 1)));
        return scenes;
    }

    /** Draws the component on a fake 480x270 GUI (1080p at GUI scale 4) and saves it 3x upscaled. */
    static void write(ActionBarSimulator client, Component component, Path file) throws IOException {
        int width = 480;
        int height = 270;
        BufferedImage gui = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int shade = 90 + (y * 60 / height);
                gui.setRGB(x, y, y > height - 60 ? 0xFF3C6E2F : 0xFF000000 | (shade / 2 << 16) | (shade << 8) | (shade + 60));
            }
        }
        // hotbar (182x22) and the heart/food rows, for orientation
        fill(gui, width / 2 - 91, height - 22, 182, 22, 0xC0202020);
        fill(gui, width / 2 - 91, height - 39, 81, 9, 0xC0B02020);
        fill(gui, width / 2 + 10, height - 39, 81, 9, 0xC0A06020);
        client.draw(component, gui, width / 2, height - 72);

        int crop = 92; // only the lower part of the screen is interesting
        BufferedImage scaled = new BufferedImage(width * 3, (height - crop) * 3, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
                scaled.setRGB(x, y, gui.getRGB(x / 3, crop + y / 3));
            }
        }
        ImageIO.write(scaled, "png", file.toFile());
    }

    private static void fill(BufferedImage image, int x0, int y0, int w, int h, int argb) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                image.setRGB(x, y, argb);
            }
        }
    }
}
