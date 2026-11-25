package io.github.filipe4fun.hangman.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GallowsTest {

    private static final String FULL_FIGURE = "/ \\";

    @Test
    @DisplayName("starts empty and shows the full figure only on the last life")
    void fullFigureOnlyAtTheEnd() {
        assertFalse(Gallows.draw(0, 6).contains("O"));
        for (int misses = 0; misses < 6; misses++) {
            assertFalse(Gallows.draw(misses, 6).contains(FULL_FIGURE), "misses=" + misses);
        }
        assertTrue(Gallows.draw(6, 6).contains(FULL_FIGURE));
    }

    @Test
    @DisplayName("every miss with six lives draws a new part")
    void eachMissAddsAPart() {
        for (int misses = 1; misses <= 6; misses++) {
            assertNotEquals(Gallows.draw(misses - 1, 6), Gallows.draw(misses, 6), "misses=" + misses);
        }
    }

    @Test
    @DisplayName("works for any number of lives and clamps out-of-range misses")
    void scalesAndClamps() {
        assertTrue(Gallows.draw(1, 1).contains(FULL_FIGURE));
        assertTrue(Gallows.draw(10, 10).contains(FULL_FIGURE));
        assertFalse(Gallows.draw(9, 10).contains(FULL_FIGURE));
        assertEquals(Gallows.draw(0, 6), Gallows.draw(-3, 6));
        assertEquals(Gallows.draw(6, 6), Gallows.draw(99, 6));
        assertEquals(7, Gallows.stageCount());
    }

    @Test
    void rejectsZeroLives() {
        assertThrows(IllegalArgumentException.class, () -> Gallows.draw(0, 0));
    }
}
