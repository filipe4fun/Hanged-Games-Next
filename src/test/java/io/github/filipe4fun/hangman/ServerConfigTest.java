package io.github.filipe4fun.hangman;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {

    @Test
    @DisplayName("uses sensible defaults with no arguments")
    void defaults() {
        assertEquals(ServerConfig.defaults(), ServerConfig.parse());
        assertEquals(8081, ServerConfig.parse().port());
        assertEquals(2, ServerConfig.parse().playersPerRound());
        assertEquals(6, ServerConfig.parse().lives());
        assertTrue(ServerConfig.parse().colors());
    }

    @Test
    @DisplayName("reads long and short options")
    void readsOptions() {
        ServerConfig config = ServerConfig.parse("--port", "9000", "-n", "4", "--lives", "8", "-w", "words.txt",
                "--no-color");
        assertEquals(9000, config.port());
        assertEquals(4, config.playersPerRound());
        assertEquals(8, config.lives());
        assertEquals(Optional.of(Path.of("words.txt")), config.wordsFile());
        assertFalse(config.colors());
    }

    @Test
    @DisplayName("keeps the original 'java ServerMain 3' style working")
    void legacyPlayerCount() {
        assertEquals(3, ServerConfig.parse("3").playersPerRound());
    }

    @Test
    @DisplayName("explains bad input instead of crashing")
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse("--port", "abc"));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse("--port"));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse("--port", "70000"));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse("--players", "0"));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse("--lives", "0"));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse("--banana"));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse("2", "3"));
    }

    @Test
    void helpIsSignalledSeparately() {
        assertThrows(ServerConfig.HelpRequested.class, () -> ServerConfig.parse("--help"));
        assertTrue(ServerConfig.USAGE.contains("--players"));
    }
}
