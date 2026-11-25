package io.github.filipe4fun.hangman.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    @Test
    @DisplayName("starts fully hidden, but shows spaces and hyphens")
    void masksLettersOnly() {
        Board board = new Board("adapta-te já", 6);
        assertEquals("_ _ _ _ _ _ - _ _   _ _", board.masked());
        assertEquals(10, board.letterCount());
        assertEquals(10, board.hiddenCount());
    }

    @Test
    @DisplayName("a correct letter reveals every occurrence")
    void hitRevealsAllOccurrences() {
        Board board = new Board("java", 6);
        assertEquals(GuessResult.HIT, board.guess("a"));
        assertEquals("_ A _ A", board.masked());
        assertEquals(6, board.livesLeft());
    }

    @Test
    @DisplayName("a wrong letter costs a life")
    void missCostsALife() {
        Board board = new Board("java", 6);
        assertEquals(GuessResult.MISS, board.guess("z"));
        assertEquals(5, board.livesLeft());
        assertEquals(1, board.misses());
    }

    @Test
    @DisplayName("repeating a letter is free and changes nothing")
    void repeatedLetterIsFree() {
        Board board = new Board("java", 6);
        board.guess("z");
        assertEquals(GuessResult.ALREADY_TRIED, board.guess("Z"));
        assertEquals(5, board.livesLeft());
    }

    @Test
    @DisplayName("empty input and symbols are rejected without costing a life")
    void invalidInput() {
        Board board = new Board("java", 6);
        assertEquals(GuessResult.INVALID, board.guess(""));
        assertEquals(GuessResult.INVALID, board.guess("   "));
        assertEquals(GuessResult.INVALID, board.guess("?"));
        assertEquals(GuessResult.INVALID, board.guess(null));
        assertEquals(6, board.livesLeft());
        assertTrue(board.tried().isEmpty());
    }

    @Test
    @DisplayName("guesses ignore case and accents")
    void accentInsensitive() {
        Board board = new Board("Código", 6);
        assertEquals(GuessResult.HIT, board.guess("o"));
        assertEquals("_ Ó _ _ _ O", board.masked());
        assertEquals(GuessResult.HIT, board.guess("C"));
        assertEquals(GuessResult.HIT, board.guess("d"));
        assertEquals(GuessResult.HIT, board.guess("í"));
        assertEquals(GuessResult.SOLVED, board.guess("g"));
        assertEquals("C Ó D I G O", board.masked());
    }

    @Test
    @DisplayName("the last missing letter solves the board")
    void solveByLetters() {
        Board board = new Board("oop", 6);
        assertEquals(GuessResult.HIT, board.guess("o"));
        assertEquals(GuessResult.SOLVED, board.guess("p"));
        assertTrue(board.isSolved());
        assertTrue(board.isOver());
        assertFalse(board.isLost());
    }

    @Test
    @DisplayName("the whole phrase can be guessed at once, ignoring extra spaces")
    void solveByWholeWord() {
        Board board = new Board("world wide web", 6);
        assertEquals(GuessResult.SOLVED, board.guess("  World   Wide web "));
        assertEquals("W O R L D   W I D E   W E B", board.masked());
        assertEquals(0, board.hiddenCount());
    }

    @Test
    @DisplayName("a wrong whole-word guess costs a life")
    void wrongWordCostsALife() {
        Board board = new Board("java", 6);
        assertEquals(GuessResult.WRONG_WORD, board.guess("kotlin"));
        assertEquals(5, board.livesLeft());
    }

    @Test
    @DisplayName("running out of lives loses the board")
    void loseAfterMaxMisses() {
        Board board = new Board("java", 3);
        assertEquals(GuessResult.MISS, board.guess("x"));
        assertEquals(GuessResult.WRONG_WORD, board.guess("scala"));
        assertEquals(GuessResult.LOST, board.guess("q"));
        assertTrue(board.isLost());
        assertEquals(0, board.livesLeft());
    }

    @Test
    @DisplayName("a finished board refuses more guesses")
    void finishedBoardRefusesGuesses() {
        Board board = new Board("a", 6);
        board.guess("a");
        assertThrows(IllegalStateException.class, () -> board.guess("b"));
    }

    @Test
    @DisplayName("rejects blank words and impossible lives")
    void validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new Board("  ", 6));
        assertThrows(IllegalArgumentException.class, () -> new Board("java", 0));
    }

    @Test
    @DisplayName("tried letters are kept in alphabetical order")
    void triedIsSorted() {
        Board board = new Board("java", 6);
        board.guess("v");
        board.guess("b");
        board.guess("a");
        assertEquals("[A, B, V]", board.tried().toString());
    }
}
