package io.github.filipe4fun.hangman.server;

/** Optional ANSI colours for terminal clients such as telnet and netcat. */
final class Style {

    private static final String RESET = "\u001B[0m";

    private final boolean enabled;

    Style(boolean enabled) {
        this.enabled = enabled;
    }

    String bold(String text) {
        return wrap("1", text);
    }

    String dim(String text) {
        return wrap("2", text);
    }

    String green(String text) {
        return wrap("32", text);
    }

    String red(String text) {
        return wrap("31", text);
    }

    String yellow(String text) {
        return wrap("33", text);
    }

    String blue(String text) {
        return wrap("1;34", text);
    }

    private String wrap(String code, String text) {
        return enabled ? "\u001B[" + code + "m" + text + RESET : text;
    }
}
