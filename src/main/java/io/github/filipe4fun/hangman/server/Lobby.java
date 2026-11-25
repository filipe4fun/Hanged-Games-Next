package io.github.filipe4fun.hangman.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Gathers players until there are enough to start a round, then starts it for all of them. */
final class Lobby {

    private final int playersPerRound;
    private final Function<List<PlayerSession>, Round> roundFactory;
    private final Style style;
    private final Map<PlayerSession, CompletableFuture<Round>> waiting = new LinkedHashMap<>();

    Lobby(int playersPerRound, Function<List<PlayerSession>, Round> roundFactory, Style style) {
        this.playersPerRound = playersPerRound;
        this.roundFactory = roundFactory;
        this.style = style;
    }

    /** Adds a player to the queue. The future completes when their round starts. */
    synchronized CompletableFuture<Round> join(PlayerSession player) {
        CompletableFuture<Round> seat = new CompletableFuture<>();
        waiting.put(player, seat);
        if (waiting.size() < playersPerRound) {
            String status = style.dim("Waiting for players... %d/%d ready.".formatted(waiting.size(), playersPerRound));
            waiting.keySet().forEach(p -> p.send(status));
            return seat;
        }
        List<PlayerSession> players = new ArrayList<>(waiting.keySet());
        List<CompletableFuture<Round>> seats = new ArrayList<>(waiting.values());
        waiting.clear();
        Round round = roundFactory.apply(players);
        round.start();
        seats.forEach(s -> s.complete(round));
        return seat;
    }

    synchronized void leave(PlayerSession player) {
        CompletableFuture<Round> seat = waiting.remove(player);
        if (seat != null) {
            seat.cancel(false);
        }
    }

    synchronized void shutdown() {
        waiting.values().forEach(s -> s.completeExceptionally(new IllegalStateException("Server stopped")));
        waiting.clear();
    }

    synchronized int waitingCount() {
        return waiting.size();
    }
}
