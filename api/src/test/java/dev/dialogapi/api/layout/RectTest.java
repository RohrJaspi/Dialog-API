package dev.dialogapi.api.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RectTest {

    @Test
    void centeredStartsLeftOfTheCentreAndAtTheTopOfTheLine() {
        assertEquals(new Rect(-164, 0, 328, 168), Rect.centered(328, 168));
        assertEquals(new Rect(-50, 0, 101, 10), Rect.centered(101, 10), "odd widths round towards the centre");
    }

    @Test
    void columnsHaveGapsAndFillTakesTheRest() {
        List<Rect> columns = Rect.centered(328, 112).columns(8, 112, Rect.FILL);
        assertEquals(new Rect(-164, 0, 112, 112), columns.get(0));
        assertEquals(new Rect(-44, 0, 208, 112), columns.get(1));
        assertEquals(164, columns.get(1).right(), "the columns end exactly at the right edge");
    }

    @Test
    void rowsStackDownwards() {
        List<Rect> rows = Rect.of(0, 10, 100, 100).rows(4, 20, Rect.FILL, 30);
        assertEquals(new Rect(0, 10, 100, 20), rows.get(0));
        assertEquals(new Rect(0, 34, 100, 42), rows.get(1));
        assertEquals(new Rect(0, 80, 100, 30), rows.get(2));
        assertEquals(110, rows.get(2).bottom());
    }

    @Test
    void withoutFillTheRestStaysEmpty() {
        List<Rect> columns = Rect.of(0, 0, 100, 10).columns(0, 30, 30);
        assertEquals(60, columns.get(1).right());
    }

    @Test
    void sizesThatDoNotFitAreRejected() {
        Rect rect = Rect.of(0, 0, 100, 100);
        assertThrows(IllegalArgumentException.class, () -> rect.columns(10, 50, 50));
        assertThrows(IllegalArgumentException.class, () -> rect.rows(0, Rect.FILL, Rect.FILL));
        assertThrows(IllegalArgumentException.class, () -> rect.rows(0, -5));
        assertThrows(IllegalArgumentException.class, () -> rect.columns(0));
        assertThrows(IllegalArgumentException.class, () -> rect.inset(60, 0));
        assertThrows(IllegalArgumentException.class, () -> rect.place(101, 10, HAlign.LEFT, VAlign.TOP));
    }

    @Test
    void insetShrinksOnAllSides() {
        assertEquals(new Rect(8, 4, 84, 42), Rect.of(0, 0, 100, 50).inset(8, 4));
        assertEquals(new Rect(5, 5, 90, 40), Rect.of(0, 0, 100, 50).inset(5));
    }

    @Test
    void placeAlignsInsideTheRectangle() {
        Rect area = Rect.of(10, 20, 100, 60);
        assertEquals(new Rect(10, 20, 30, 10), area.place(30, 10, HAlign.LEFT, VAlign.TOP));
        assertEquals(new Rect(45, 45, 30, 10), area.place(30, 10, HAlign.CENTER, VAlign.MIDDLE));
        assertEquals(new Rect(80, 70, 30, 10), area.place(30, 10, HAlign.RIGHT, VAlign.BOTTOM));
    }

    @Test
    void alignmentsComputeThePositionOfAnElement() {
        Rect area = Rect.of(-10, 0, 40, 40);
        assertEquals(-10, HAlign.LEFT.x(area, 8));
        assertEquals(6, HAlign.CENTER.x(area, 8));
        assertEquals(22, HAlign.RIGHT.x(area, 8));
        assertEquals(0, VAlign.TOP.top(area, 8));
        assertEquals(16, VAlign.MIDDLE.top(area, 8));
        assertEquals(32, VAlign.BOTTOM.top(area, 8));
    }
}
