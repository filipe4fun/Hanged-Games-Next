package io.github.filipe4fun.hangman.server;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** One connected player. Runs on its own virtual thread for the whole connection. */
final class PlayerSession implements Runnable, Closeable {

    static final String QUIT = "/quit";
    private static final Pattern VALID_NAME = Pattern.compile("[\\p{L}\\p{N}_-]{1,16}");
    // Control characters, including telnet negotiation bytes that arrive as replacement characters.
    private static final Pattern NOISE = Pattern.compile("[\\p{Cntrl}\\uFFFD\\u00FF]");

    private final Socket socket;
    private final GameServer server;
    private final Style style;
    private final BufferedReader in;
    private final PrintWriter out;
    private volatile String name = "?";
    private volatile Round currentRound;

    PlayerSession(Socket socket, GameServer server, Style style) throws IOException {
        this.socket = socket;
        this.server = server;
        this.style = style;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    @Override
    public void run() {
        try {
            send(style.blue(Banners.WELCOME) + "\n");
            if (!askName()) {
                return;
            }
            boolean again = true;
            while (again && !socket.isClosed()) {
                currentRound = server.lobby().join(this).get();
                again = play(currentRound);
                currentRound = null;
            }
            send("Thanks for playing, " + name + "!");
        } catch (Disconnected | IOException e) {
            // The player closed the connection; nothing to tell them.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            send(style.red("The server is shutting down."));
        } finally {
            Round round = currentRound;
            if (round != null) {
                round.leave(this);
            }
            server.remove(this);
            close();
        }
    }

    private boolean askName() throws IOException {
        while (true) {
            send("Choose a name (letters, numbers, _ or -, up to 16 characters):");
            String candidate = readLine();
            if (candidate.equalsIgnoreCase(QUIT)) {
                return false;
            }
            if (!VALID_NAME.matcher(candidate).matches()) {
                send(style.yellow("That name won't work. Try something like ada_lovelace."));
            } else if (!server.claimName(this, candidate)) {
                send(style.yellow("Someone is already playing as " + candidate + ". Pick another name."));
            } else {
                name = candidate;
                send("Welcome, " + style.bold(name) + "!");
                return true;
            }
        }
    }

    /** Plays one round. Returns true when the player wants another one. */
    private boolean play(Round round) throws IOException, InterruptedException {
        while (true) {
            if (round.isFinished()) {
                return wantsAnother(readLine());
            }
            if (round.isOut(this)) {
                round.awaitEnd(Long.MAX_VALUE, TimeUnit.DAYS);
                continue;
            }
            out.print("guess> ");
            out.flush();
            String line = readLine();
            if (round.isFinished()) {
                return wantsAnother(line);
            }
            if (line.equalsIgnoreCase(QUIT)) {
                round.leave(this);
                return false;
            }
            round.guess(this, line);
        }
    }

    private static boolean wantsAnother(String answer) {
        return !answer.equalsIgnoreCase(QUIT);
    }

    private String readLine() throws IOException {
        String line = in.readLine();
        if (line == null) {
            throw new Disconnected();
        }
        return NOISE.matcher(line).replaceAll("").strip();
    }

    void send(String message) {
        out.println(message);
    }

    String name() {
        return name;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private static final class Disconnected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Disconnected() {
            super("client disconnected", null, false, false);
        }
    }
}
