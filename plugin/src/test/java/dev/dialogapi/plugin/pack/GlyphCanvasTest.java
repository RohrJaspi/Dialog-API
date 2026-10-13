package dev.dialogapi.plugin.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dialogapi.api.glyph.Glyph;
import dev.dialogapi.api.glyph.GlyphCanvas;
import dev.dialogapi.api.glyph.OffsetChars;
import dev.dialogapi.api.glyph.TextFontRef;
import dev.dialogapi.api.layout.HAlign;
import dev.dialogapi.api.screen.ScreenBody;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class GlyphCanvasTest {

    private final TestPacks.Built built = TestPacks.demo();
    private final DemoCard.Handles card = built.card();
    private final BodySimulator client = new BodySimulator(built.packDir());

    @Test
    void aLineOfAbsolutelyPlacedGlyphsHasAnAdvanceOfZero() {
        Component line = new GlyphCanvas()
                .glyph(card.wide().ref(), -164)
                .glyph(card.rim().ref(), -164, TextColor.color(0xFFB347))
                .glyph(card.icon().ref(), -48)
                .build();
        assertEquals(0, client.width(line));
    }

    @Test
    void positionsAreRelativeToTheStartOfTheLine() {
        Component line = new GlyphCanvas().glyph(card.icon().ref(), 9).build();
        assertEquals(0, client.width(line));
        assertEquals(9 + card.icon().ref().glyph().advance(), client.maxRunningWidth(line));
    }

    @Test
    void goingBackwardsKeepsTheRunningWidthLow() {
        Glyph wide = card.wide().ref().glyph();
        int x = -wide.width() / 2;
        Component line = new GlyphCanvas()
                .glyph(wide, x)
                .glyph(card.rim().ref(), x)
                .glyph(card.icon().ref(), -card.icon().ref().width() / 2)
                .build();
        assertEquals(x + wide.advance(), client.maxRunningWidth(line));
    }

    @Test
    void textIsPlacedWithItsExactWidthSoTheLineStaysAtZero() {
        TextFontRef font = card.value().font();
        Component line = new GlyphCanvas()
                .glyph(card.wide().ref(), -164)
                .text("Verschneite Ebene", font, -40, NamedTextColor.WHITE)
                .build();
        assertEquals(0, client.width(line));
        TextComponent run = (TextComponent) line.children().get(1);
        assertEquals(font.key(), run.font());
        assertEquals(TextDecoration.State.FALSE, run.decoration(TextDecoration.BOLD));
    }

    @Test
    void textInASlotIsFittedAndRightAligned() {
        Component line = new GlyphCanvas()
                .text("Ein sehr sehr sehr sehr sehr langer Biomname", card.value().font(), 0, 60, HAlign.RIGHT,
                        NamedTextColor.WHITE)
                .build();
        assertEquals(0, client.width(line));
        assertEquals(60, client.maxRunningWidth(line), "right aligned: the text ends exactly at the slot edge");
        assertTrue(PlainTextComponentSerializer.plainText().serialize(line).contains("…")
                || PlainTextComponentSerializer.plainText().serialize(line).contains("..."), "shortened");
    }

    @Test
    void inlineGlyphAdvancesExactlyToTheTextStart() {
        assertEquals(20, client.width(GlyphCanvas.inline(card.icon().ref(), 20)));
    }

    @Test
    void glyphRunsAreTintedAndHaveNoShadow() {
        Component line = new GlyphCanvas().glyph(card.rim().ref(), -164, NamedTextColor.GOLD).build();
        TextComponent run = (TextComponent) line.children().getFirst();
        assertEquals(NamedTextColor.GOLD, run.color());
        assertEquals(ShadowColor.none(), run.shadowColor());
        assertEquals(GlyphCanvas.GLYPHS_FONT, run.font());
    }

    @Test
    void untintedGlyphsAreWhiteSoTheBookDoesNotDrawThemBlack() {
        Component line = new GlyphCanvas().glyph(card.icon().ref(), 41).build();
        TextComponent run = (TextComponent) line.children().getFirst();
        assertEquals(NamedTextColor.WHITE, run.color());
    }

    @Test
    void spaceMovesTheCursorWithoutDrawing() {
        assertEquals(20, client.width(GlyphCanvas.space(20)));
        assertEquals(-5, client.width(GlyphCanvas.space(-5)));
    }

    @Test
    void textAppendedToSpaceKeepsTheDefaultFont() {
        Component line = GlyphCanvas.space(20).append(Component.text("Selten"));
        TextComponent text = (TextComponent) line.children().getLast();
        assertEquals(null, text.font());
        assertEquals(20 + 6 * 6, client.width(line));
    }

    @Test
    void styledComponentsAreAccountedWithTheirAdvance() {
        Component line = new GlyphCanvas()
                .styled(Component.text(OffsetChars.encode(8), Style.style().font(GlyphCanvas.SPACE_FONT).build()), 8, 30)
                .build();
        assertEquals(0, client.width(line));
    }

    // ------------------------------------------------------------------------------ the demo card as a whole

    @Test
    void theCardLineHasNoAdvanceAndNeverGetsWiderThanItsBody() {
        Component line = DemoCard.line(card, "Excalibur", "Legendary sword", "Damage", "12 – 18", NamedTextColor.GOLD);
        assertEquals(0, client.width(line));
        assertTrue(client.maxRunningWidth(line) <= DemoCard.CARD.right() - DemoCard.CARD.x(), "stays inside the card");
    }

    @Test
    void theBodyReservesExactlyTheHeightOfTheDrawing() {
        ScreenBody.Text body = (ScreenBody.Text) DemoCard.body(card, "Excalibur", "Legendary sword", "Damage", "12", NamedTextColor.GOLD);
        int lines = client.lineCount(body.content(), body.width());
        assertEquals((DemoCard.CARD.height() + 2 + ScreenBody.LINE_HEIGHT - 1) / ScreenBody.LINE_HEIGHT, lines);
    }

    @Test
    void aTooLongTitleIsShortenedToItsBox() {
        Component line = DemoCard.line(card, "Riesenhafter Tiefseeschuppenwels der Sümpfe", "x", "y", "z", NamedTextColor.GOLD);
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        assertTrue(plain.contains("…") || plain.contains("..."));
        assertEquals(0, client.width(line));
    }
}
