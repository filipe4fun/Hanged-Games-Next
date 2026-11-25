package io.github.filipe4fun.hangman.game;

import java.text.Normalizer;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * One player's view of a hangman round: the secret word, the letters tried so far and the lives left.
 *
 * <p>Guesses are case- and accent-insensitive, so {@code e} reveals {@code É}. Characters that are not
 * letters or digits (spaces, hyphens, apostrophes) are shown from the start and never need guessing.
 *
 * <p>A board belongs to a single player and is not thread-safe.
 */
public final class Board {

    private static final char HIDDEN = '_';

    private final String secret;
    private final String normalizedSecret;
    private final int maxMisses;
    private final SortedSet<Character> tried = new TreeSet<>();
    private int misses;
    private boolean solved;

    public Board(String secret, int maxMisses) {
        Objects.requireNonNull(secret, "secret");
        if (secret.isBlank()) {
            throw new IllegalArgumentException("The secret word cannot be blank");
        }
        if (maxMisses < 1) {
            throw new IllegalArgumentException("maxMisses must be at least 1, was " + maxMisses);
        }
        this.secret = secret.strip().toUpperCase();
        this.normalizedSecret = normalize(this.secret);
        this.maxMisses = maxMisses;
    }

    /**
     * Applies a guess: a single character tries one letter, anything longer tries the whole word.
     */
    public GuessResult guess(String input) {
        if (isOver()) {
            throw new IllegalStateException("This board is already finished");
        }
        if (input == null || input.isBlank()) {
            return GuessResult.INVALID;
        }
        String guess = normalize(input.strip().toUpperCase()).replaceAll("\\s+", " ");
        return guess.length() == 1 ? guessLetter(guess.charAt(0)) : guessWord(guess);
    }

    private GuessResult guessLetter(char letter) {
        if (!Character.isLetterOrDigit(letter)) {
            return GuessResult.INVALID;
        }
        if (!tried.add(letter)) {
            return GuessResult.ALREADY_TRIED;
        }
        if (normalizedSecret.indexOf(letter) < 0) {
            return miss(GuessResult.MISS);
        }
        if (hiddenCount() == 0) {
            solved = true;
            return GuessResult.SOLVED;
        }
        return GuessResult.HIT;
    }

    private GuessResult guessWord(String word) {
        if (word.equals(normalizedSecret)) {
            normalizedSecret.chars().filter(Character::isLetterOrDigit).forEach(c -> tried.add((char) c));
            solved = true;
            return GuessResult.SOLVED;
        }
        return miss(GuessResult.WRONG_WORD);
    }

    private GuessResult miss(GuessResult result) {
        misses++;
        return misses >= maxMisses ? GuessResult.LOST : result;
    }

    /** The word with unguessed letters replaced by underscores, spaced out for readability. */
    public String masked() {
        StringBuilder out = new StringBuilder(secret.length() * 2);
        for (int i = 0; i < secret.length(); i++) {
            char original = secret.charAt(i);
            char normal = normalizedSecret.charAt(i);
            boolean visible = !Character.isLetterOrDigit(normal) || tried.contains(normal);
            if (i > 0) {
                out.append(' ');
            }
            out.append(visible ? original : HIDDEN);
        }
        return out.toString();
    }

    /** Number of letter positions still hidden. */
    public int hiddenCount() {
        int hidden = 0;
        for (int i = 0; i < normalizedSecret.length(); i++) {
            char c = normalizedSecret.charAt(i);
            if (Character.isLetterOrDigit(c) && !tried.contains(c)) {
                hidden++;
            }
        }
        return hidden;
    }

    /** Number of letter positions in the word. */
    public int letterCount() {
        return (int) normalizedSecret.chars().filter(Character::isLetterOrDigit).count();
    }

    public int misses() {
        return misses;
    }

    public int maxMisses() {
        return maxMisses;
    }

    public int livesLeft() {
        return maxMisses - misses;
    }

    public SortedSet<Character> tried() {
        return Collections.unmodifiableSortedSet(tried);
    }

    public boolean isSolved() {
        return solved;
    }

    public boolean isLost() {
        return misses >= maxMisses;
    }

    public boolean isOver() {
        return isSolved() || isLost();
    }

    public String secret() {
        return secret;
    }

    /** Upper-cases and strips accents, keeping one character per input character. */
    static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            String decomposed = Normalizer.normalize(String.valueOf(text.charAt(i)), Normalizer.Form.NFD);
            out.append(Character.toUpperCase(decomposed.charAt(0)));
        }
        return out.toString();
    }
}
