package io.github.filipe4fun.hangman;

import io.github.filipe4fun.hangman.game.WordBank;
import io.github.filipe4fun.hangman.server.GameServer;

import java.io.IOException;
import java.security.SecureRandom;

/** Starts a Hanged Games server. Players connect with {@code telnet <host> <port>} or {@code nc <host> <port>}. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tT %4$s %5$s%6$s%n");
        ServerConfig config;
        try {
            config = ServerConfig.parse(args);
        } catch (ServerConfig.HelpRequested e) {
            System.out.print(ServerConfig.USAGE);
            return;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.print(ServerConfig.USAGE);
            System.exit(2);
            return;
        }

        try {
            WordBank words = config.wordsFile().isPresent()
                    ? WordBank.fromFile(config.wordsFile().get())
                    : WordBank.bundled();
            GameServer server = new GameServer(config, words, new SecureRandom()).start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "shutdown"));
            System.out.printf("Hanged Games is running. Players can join with: nc localhost %d%n", server.port());
            server.awaitTermination();
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not start the server: " + e.getMessage());
            System.exit(1);
        }
    }
}
