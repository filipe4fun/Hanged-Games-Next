package io.github.filipe4fun.hangman.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordBankTest {

    @Test
    @DisplayName("skips blank lines and comments, and trims words")
    void skipsCommentsAndBlanks() throws IOException {
        String text = "# a comment\n\n  java  \nkafka\n   \n# another\nspring boot\n";
        WordBank bank = WordBank.fromStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("java", "kafka", "spring boot"), bank.words());
    }

    @Test
    @DisplayName("removes duplicates, ignoring case")
    void removesDuplicates() {
        WordBank bank = new WordBank(List.of("magnific0x", "Magnific0x", "MAGNIFIC0X", "java"));
        assertEquals(List.of("magnific0x", "java"), bank.words());
    }

    @Test
    @DisplayName("refuses a list with no words")
    void refusesEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> new WordBank(List.of("# only a comment", "")));
    }

    @Test
    @DisplayName("always picks a word from the list")
    void picksFromList() {
        WordBank bank = new WordBank(List.of("java", "kafka", "aws"));
        Random random = new Random(42);
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String word = bank.pick(random);
            assertTrue(bank.words().contains(word));
            picked.add(word);
        }
        assertEquals(3, picked.size(), "every word should come up over 100 picks");
    }

    @Test
    @DisplayName("reads a custom list from a file")
    void readsFile() throws IOException {
        Path file = Files.createTempFile("words", ".txt");
        try {
            Files.writeString(file, "francesinha\nbifanas\n", StandardCharsets.UTF_8);
            assertEquals(List.of("francesinha", "bifanas"), WordBank.fromFile(file).words());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("the bundled list loads, has no duplicates and every word is playable")
    void bundledListIsValid() {
        WordBank bank = WordBank.bundled();
        assertTrue(bank.size() >= 20);
        Set<String> seen = new HashSet<>();
        for (String word : bank.words()) {
            assertTrue(seen.add(word.toLowerCase(Locale.ROOT)), "duplicate: " + word);
            Board board = new Board(word, 6);
            assertTrue(board.letterCount() > 0, "no letters in: " + word);
            assertFalse(board.isOver());
        }
    }
}
