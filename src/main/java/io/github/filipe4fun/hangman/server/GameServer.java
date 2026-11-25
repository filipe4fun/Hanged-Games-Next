package io.github.filipe4fun.hangman.server;

import io.github.filipe4fun.hangman.ServerConfig;
import io.github.filipe4fun.hangman.game.WordBank;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

/**
 * Accepts players over TCP and runs hangman rounds for them. Every connection gets its own virtual thread,
 * so a slow or idle player never blocks anyone else. The server keeps running between rounds.
 */
public final class GameServer implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(GameServer.class.getName());

    private final ServerConfig config;
    private final WordBank words;
    private final RandomGenerator random;
    private final Style style;
    private final Lobby lobby;
    private final Map<String, PlayerSession> names = new ConcurrentHashMap<>();
    private final Map<PlayerSession, Boolean> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger rounds = new AtomicInteger();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private ServerSocket serverSocket;
    private Thread acceptor;

    public GameServer(ServerConfig config, WordBank words, RandomGenerator random) {
        this.config = config;
        this.words = words;
        this.random = random;
        this.style = new Style(config.colors());
        this.lobby = new Lobby(config.playersPerRound(),
                players -> new Round(rounds.incrementAndGet(), words.pick(random), config.lives(), players, style),
                style);
    }

    /** Binds the port and starts accepting players in the background. */
    public synchronized GameServer start() throws IOException {
        if (serverSocket != null) {
            throw new IllegalStateException("Server already started");
        }
        serverSocket = new ServerSocket(config.port());
        acceptor = Thread.ofVirtual().name("acceptor").start(this::acceptLoop);
        LOG.log(System.Logger.Level.INFO, "Listening on port %d, %d player(s) per round, %d words loaded"
                .formatted(port(), config.playersPerRound(), words.size()));
        return this;
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                PlayerSession session = new PlayerSession(socket, this, style);
                sessions.put(session, Boolean.TRUE);
                executor.submit(session);
                LOG.log(System.Logger.Level.INFO, "Player connected from {0}", socket.getRemoteSocketAddress());
            } catch (SocketException e) {
                return; // The server socket was closed.
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not accept a connection", e);
            }
        }
    }

    /** The port the server is listening on, useful when it was started on port 0. */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /** Waits until the server stops. */
    public void awaitTermination() throws InterruptedException {
        acceptor.join();
    }

    Lobby lobby() {
        return lobby;
    }

    boolean claimName(PlayerSession session, String name) {
        return names.putIfAbsent(name.toLowerCase(Locale.ROOT), session) == null;
    }

    void remove(PlayerSession session) {
        sessions.remove(session);
        lobby.leave(session);
        names.values().remove(session);
        LOG.log(System.Logger.Level.INFO, "Player {0} disconnected", session.name());
    }

    int rounds() {
        return rounds.get();
    }

    @Override
    public void close() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Error closing server socket", e);
        }
        lobby.shutdown();
        sessions.keySet().forEach(PlayerSession::close);
        executor.shutdownNow();
    }
}
