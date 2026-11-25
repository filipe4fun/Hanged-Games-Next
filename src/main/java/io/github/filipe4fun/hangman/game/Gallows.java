package io.github.filipe4fun.hangman.game;

import java.util.List;

/** ASCII gallows that fills in as a player loses lives. */
public final class Gallows {

    private static final List<String> STAGES = List.of(
            """
              +---+
              |   |
                  |
                  |
                  |
                  |
            =========""",
            """
              +---+
              |   |
              O   |
                  |
                  |
                  |
            =========""",
            """
              +---+
              |   |
              O   |
              |   |
                  |
                  |
            =========""",
            """
              +---+
              |   |
              O   |
             /|   |
                  |
                  |
            =========""",
            """
              +---+
              |   |
              O   |
             /|\\  |
                  |
                  |
            =========""",
            """
              +---+
              |   |
              O   |
             /|\\  |
             /    |
                  |
            =========""",
            """
              +---+
              |   |
              O   |
             /|\\  |
             / \\  |
                  |
            =========""");

    private Gallows() {
    }

    /** Number of drawings, from the empty gallows to the full figure. */
    public static int stageCount() {
        return STAGES.size();
    }

    /**
     * The drawing for a player who has used {@code misses} of {@code maxMisses} lives. Any number of lives
     * is scaled onto the six-step drawing, and the full figure only appears on the last life.
     */
    public static String draw(int misses, int maxMisses) {
        if (maxMisses < 1) {
            throw new IllegalArgumentException("maxMisses must be at least 1");
        }
        int clamped = Math.max(0, Math.min(misses, maxMisses));
        int last = STAGES.size() - 1;
        int stage = clamped == maxMisses ? last : Math.min(last - 1, clamped * last / maxMisses);
        return STAGES.get(stage);
    }
}
