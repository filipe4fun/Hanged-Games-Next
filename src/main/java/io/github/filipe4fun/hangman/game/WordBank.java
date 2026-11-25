package io.github.filipe4fun.hangman.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The pool of secret words. One word or phrase per line; blank lines and lines starting with {@code #}
 * are ignored, and duplicates (ignoring case) are removed.
 */
public final class WordBank {

    private static final String DEFAULT_RESOURCE = "/words.txt";

    private final List<String> words;

    public WordBank(List<String> words) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : words) {
            String word = raw == null ? "" : raw.strip();
            if (word.isEmpty() || word.startsWith("#")) {
                continue;
            }
            if (seen.add(word.toLowerCase(Locale.ROOT))) {
                unique.add(word);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("The word list has no words");
        }
        this.words = List.copyOf(unique);
    }

    /** The word list bundled with the game. */
    public static WordBank bundled() {
        try (InputStream in = WordBank.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource " + DEFAULT_RESOURCE);
            }
            return fromStream(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a word list from a UTF-8 text file. */
    public static WordBank fromFile(Path path) throws IOException {
        return new WordBank(Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    static WordBank fromStream(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return new WordBank(reader.lines().toList());
        }
    }

    public String pick(RandomGenerator random) {
        return words.get(random.nextInt(words.size()));
    }

    public List<String> words() {
        return words;
    }

    public int size() {
        return words.size();
    }
}
