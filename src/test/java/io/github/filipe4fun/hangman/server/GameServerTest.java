package io.github.filipe4fun.hangman.server;

import io.github.filipe4fun.hangman.ServerConfig;
import io.github.filipe4fun.hangman.game.WordBank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** End-to-end tests: a real server on a free port, and real socket clients typing like a player would. */
class GameServerTest {

    private GameServer server;
    private final List<Client> clients = new ArrayList<>();

    private GameServer startServer(int players, int lives, String... words) throws IOException {
        ServerConfig config = new ServerConfig(0, players, lives, Optional.empty(), false);
        server = new GameServer(config, new WordBank(List.of(words)), new Random(1)).start();
        return server;
    }

    private Client join(String name) throws IOException {
        Client client = new Client(server.port());
        clients.add(client);
        client.expect("Choose a name");
        client.say(name);
        client.expect("Welcome, " + name);
        return client;
    }

    @AfterEach
    void tearDown() {
        clients.forEach(Client::close);
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("the first player to finish the word wins, and everyone is told")
    void firstToSolveWins() throws IOException {
        startServer(2, 6, "java");
        Client alice = join("alice");
        Client bob = join("bob");
        alice.expect("=== Round 1 ===");
        bob.expect("Players: alice, bob");

        alice.say("j");
        alice.expect("J _ _ _");
        bob.expect("alice revealed a letter (1/4)");
        alice.say("a");
        alice.say("v");

        alice.expect("You guessed it first: JAVA");
        assertFalse(bob.transcript().contains("You guessed it first"));
        bob.expect("alice guessed it first. The word was JAVA.");
        bob.expect(Round.PLAY_AGAIN_PROMPT);
    }

    @Test
    @DisplayName("the whole word can be guessed in one go")
    void wholeWordGuess() throws IOException {
        startServer(1, 6, "world wide web");
        Client solo = join("solo");
        solo.expect("_ _ _ _ _   _ _ _ _   _ _ _");
        solo.say("world wide web");
        solo.expect("You guessed it first: WORLD WIDE WEB");
    }

    @Test
    @DisplayName("when everyone runs out of lives, nobody wins and the word is revealed")
    void everyoneLoses() throws IOException {
        startServer(2, 2, "java");
        Client alice = join("alice");
        Client bob = join("bob");
        alice.expect("Lives: 2/2");
        alice.say("x");
        alice.expect("Lives: 1/2");
        alice.say("y");
        alice.expect("Out of lives!");
        bob.expect("alice is out of lives.");
        bob.say("kotlin");
        bob.say("scala");
        alice.expect("Nobody guessed it. The word was JAVA.");
        bob.expect("Nobody guessed it. The word was JAVA.");
    }

    @Test
    @DisplayName("a wrong letter does not affect the other players")
    void boardsAreIndependent() throws IOException {
        startServer(2, 6, "java");
        Client alice = join("alice");
        Client bob = join("bob");
        alice.say("z");
        alice.expect("Lives: 5/6");
        bob.say("a");
        bob.expect("Lives: 6/6");
    }

    @Test
    @DisplayName("names must be valid and unique")
    void rejectsBadNames() throws IOException {
        startServer(3, 6, "java");
        join("alice");
        Client other = new Client(server.port());
        clients.add(other);
        other.expect("Choose a name");
        other.say("has spaces!");
        other.expect("That name won't work");
        other.say("ALICE");
        other.expect("Someone is already playing as ALICE");
        other.say("bob");
        other.expect("Welcome, bob");
        other.expect("Waiting for players... 2/3 ready.");
    }

    @Test
    @DisplayName("players can play again, and the server keeps running between rounds")
    void playAgain() throws IOException {
        startServer(2, 6, "java");
        Client alice = join("alice");
        Client bob = join("bob");
        alice.say("java");
        alice.expect(Round.PLAY_AGAIN_PROMPT);
        bob.expect(Round.PLAY_AGAIN_PROMPT);

        alice.say("");
        alice.expect("Waiting for players... 1/2 ready.");
        bob.say("");
        alice.expect("=== Round 2 ===");
        bob.expect("=== Round 2 ===");
        assertEquals(2, server.rounds());
    }

    @Test
    @DisplayName("if a player disconnects mid-round, the others can still finish it")
    void disconnectDoesNotBlockTheRound() throws IOException {
        startServer(2, 1, "java");
        Client alice = join("alice");
        Client bob = join("bob");
        alice.expect("=== Round 1 ===");
        bob.close();
        alice.expect("bob left the round.");
        alice.say("z");
        alice.expect("Nobody guessed it. The word was JAVA.");
    }

    @Test
    @DisplayName("typing /quit leaves the game politely")
    void quit() throws IOException {
        startServer(1, 6, "java");
        Client solo = join("solo");
        solo.expect("guess>");
        solo.say("/quit");
        solo.expect("Thanks for playing, solo!");
        assertTrue(solo.isClosedByServer());
    }

    @Test
    @DisplayName("the secret word is never sent before the round ends")
    void doesNotLeakTheWord() throws IOException {
        startServer(1, 6, "kafka");
        Client solo = join("solo");
        solo.expect("guess>");
        assertFalse(solo.transcript().contains("KAFKA"));
    }

    /** A minimal telnet-like player that waits for expected text with a timeout. */
    private final class Client implements AutoCloseable {
        private static final long TIMEOUT_MS = 5_000;
        private final Socket socket;
        private final Reader in;
        private final PrintWriter out;
        private final StringBuilder seen = new StringBuilder();
        private int cursor;

        Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(100);
            in = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void say(String line) {
            out.println(line);
        }

        /** Waits until {@code text} appears after everything matched so far. */
        void expect(String text) throws IOException {
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                int found = seen.indexOf(text, cursor);
                if (found >= 0) {
                    cursor = found + text.length();
                    return;
                }
                if (!readSome()) {
                    break;
                }
            }
            fail("Expected to see \"" + text + "\" but got:\n" + seen.substring(cursor));
        }

        /** Reads what is available. Returns false once the server closed the connection. */
        private boolean readSome() throws IOException {
            char[] buffer = new char[4096];
            try {
                int n = in.read(buffer);
                if (n < 0) {
                    return false;
                }
                seen.append(buffer, 0, n);
            } catch (SocketTimeoutException e) {
                // Nothing yet; keep waiting.
            }
            return true;
        }

        boolean isClosedByServer() throws IOException {
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                if (!readSome()) {
                    return true;
                }
            }
            return false;
        }

        String transcript() {
            return seen.toString();
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }
}
