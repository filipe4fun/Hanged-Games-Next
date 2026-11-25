package io.github.filipe4fun.hangman.game;

/** What happened after a player made a guess. */
public enum GuessResult {
    /** The letter is in the word and was revealed. */
    HIT,
    /** The letter is not in the word; the player lost a life. */
    MISS,
    /** A whole-word guess that was wrong; the player lost a life. */
    WRONG_WORD,
    /** The letter was already tried; nothing changes. */
    ALREADY_TRIED,
    /** The input is not a letter, digit or word; nothing changes. */
    INVALID,
    /** The guess completed the word. */
    SOLVED,
    /** The guess used the player's last life. */
    LOST;

    /** True when this result ends the player's game. */
    public boolean isFinal() {
        return this == SOLVED || this == LOST;
    }
}
