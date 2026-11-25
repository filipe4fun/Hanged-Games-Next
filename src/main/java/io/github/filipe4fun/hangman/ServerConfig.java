package io.github.filipe4fun.hangman;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Server settings, parsed from the command line.
 *
 * @param port             TCP port to listen on ({@code 0} picks a free one)
 * @param playersPerRound  how many players must join before a round starts
 * @param lives            wrong guesses a player can make before they are out
 * @param wordsFile        optional custom word list; the bundled one is used when empty
 * @param colors           whether to send ANSI colours to clients
 */
public record ServerConfig(int port, int playersPerRound, int lives, Optional<Path> wordsFile, boolean colors) {

    public static final int DEFAULT_PORT = 8081;
    public static final int DEFAULT_PLAYERS = 2;
    public static final int DEFAULT_LIVES = 6;

    public static final String USAGE = """
            Usage: java -jar hanged-games.jar [options]

              -p, --port <n>       TCP port to listen on (default %d)
              -n, --players <n>    players needed to start a round (default %d)
              -l, --lives <n>      wrong guesses allowed per player, 1-26 (default %d)
              -w, --words <file>   custom word list, one word or phrase per line
                  --no-color       send plain text without ANSI colours
              -h, --help           show this help

            A single number on its own is read as --players, like the original version.
            """.formatted(DEFAULT_PORT, DEFAULT_PLAYERS, DEFAULT_LIVES);

    public ServerConfig {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, was " + port);
        }
        if (playersPerRound < 1 || playersPerRound > 64) {
            throw new IllegalArgumentException("Players per round must be between 1 and 64, was " + playersPerRound);
        }
        if (lives < 1 || lives > 26) {
            throw new IllegalArgumentException("Lives must be between 1 and 26, was " + lives);
        }
        wordsFile = wordsFile == null ? Optional.empty() : wordsFile;
    }

    public static ServerConfig defaults() {
        return new ServerConfig(DEFAULT_PORT, DEFAULT_PLAYERS, DEFAULT_LIVES, Optional.empty(), true);
    }

    /** Thrown for {@code --help}, so callers can print usage and exit cleanly. */
    public static final class HelpRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public HelpRequested() {
            super("help requested", null, false, false);
        }
    }

    public static ServerConfig parse(String... args) {
        int port = DEFAULT_PORT;
        int players = DEFAULT_PLAYERS;
        int lives = DEFAULT_LIVES;
        Path words = null;
        boolean colors = true;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> throw new HelpRequested();
                case "-p", "--port" -> port = number(arg, value(args, ++i, arg));
                case "-n", "--players" -> players = number(arg, value(args, ++i, arg));
                case "-l", "--lives" -> lives = number(arg, value(args, ++i, arg));
                case "-w", "--words" -> words = Path.of(value(args, ++i, arg));
                case "--no-color" -> colors = false;
                default -> {
                    if (args.length == 1 && arg.chars().allMatch(Character::isDigit)) {
                        players = number("players", arg);
                    } else {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                }
            }
        }
        return new ServerConfig(port, players, lives, Optional.ofNullable(words), colors);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static int number(String option, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number for " + option + ", got '" + value + "'");
        }
    }
}
