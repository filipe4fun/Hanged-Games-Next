package io.github.filipe4fun.hangman.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The original team's ASCII art, loaded from resources. */
final class Banners {

    static final String WELCOME = load("welcome.txt");
    static final String YOU_WIN = load("you-win.txt");
    static final String YOU_LOSE = load("you-lose.txt");

    private Banners() {
    }

    private static String load(String name) {
        try (InputStream in = Banners.class.getResourceAsStream("/banners/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing banner " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
