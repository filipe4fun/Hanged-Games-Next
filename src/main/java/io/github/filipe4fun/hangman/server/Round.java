package io.github.filipe4fun.hangman.server;

import io.github.filipe4fun.hangman.game.Board;
import io.github.filipe4fun.hangman.game.Gallows;
import io.github.filipe4fun.hangman.game.GuessResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * One race to guess the same secret word. Every player gets their own board; the first to solve it wins,
 * and the round also ends when everyone still playing has run out of lives or left.
 *
 * <p>All state changes go through this object's monitor, so guesses from different player threads are
 * applied one at a time.
 */
final class Round {

    static final String PLAY_AGAIN_PROMPT = "Press Enter to play again, or type /quit to leave.";

    private final int number;
    private final String secret;
    private final Style style;
    private final Map<PlayerSession, Board> boards = new LinkedHashMap<>();
    private final CountDownLatch ended = new CountDownLatch(1);
    private PlayerSession winner;
    private boolean finished;

    Round(int number, String secret, int lives, List<PlayerSession> players, Style style) {
        this.number = number;
        this.secret = secret.strip().toUpperCase();
        this.style = style;
        players.forEach(p -> boards.put(p, new Board(secret, lives)));
    }

    synchronized void start() {
        String names = boards.keySet().stream().map(PlayerSession::name).collect(Collectors.joining(", "));
        for (Map.Entry<PlayerSession, Board> entry : boards.entrySet()) {
            Board board = entry.getValue();
            entry.getKey().send("""

                    %s
                    Players: %s
                    The word has %d letters. First to guess it wins. You have %d lives.
                    Guess one letter at a time, or type the whole word if you think you know it.
                    """.formatted(style.blue("=== Round " + number + " ==="), names, board.letterCount(),
                    board.maxMisses()));
            entry.getKey().send(render(board));
        }
    }

    /** Applies a guess from {@code player} and tells everyone what changed. */
    synchronized GuessResult guess(PlayerSession player, String input) {
        Board board = boards.get(player);
        if (finished || board == null || board.isOver()) {
            return GuessResult.INVALID;
        }
        GuessResult result = board.guess(input);
        switch (result) {
            case INVALID -> player.send(style.yellow("Type a single letter, or the whole word."));
            case ALREADY_TRIED -> player.send(style.yellow("You already tried that letter."));
            case HIT -> {
                player.send(style.green("Nice, that letter is in the word!") + "\n" + render(board));
                broadcastExcept(player, style.dim("%s revealed a letter (%d/%d).".formatted(
                        player.name(), board.letterCount() - board.hiddenCount(), board.letterCount())));
            }
            case MISS, WRONG_WORD -> {
                String what = result == GuessResult.MISS ? "Not in the word." : "That's not the word.";
                player.send(style.red(what) + "\n" + render(board));
            }
            case SOLVED -> finish(player);
            case LOST -> {
                player.send(render(board) + "\n" + style.red("Out of lives! Waiting for the round to end..."));
                broadcastExcept(player, style.dim(player.name() + " is out of lives."));
                if (everyoneIsOut()) {
                    finish(null);
                }
            }
        }
        return result;
    }

    /** Removes a player who disconnected or quit mid-round. */
    synchronized void leave(PlayerSession player) {
        if (boards.remove(player) == null || finished) {
            return;
        }
        broadcastExcept(player, style.dim(player.name() + " left the round."));
        if (everyoneIsOut()) {
            finish(null);
        }
    }

    private boolean everyoneIsOut() {
        return boards.values().stream().allMatch(Board::isOver);
    }

    private void finish(PlayerSession roundWinner) {
        finished = true;
        winner = roundWinner;
        for (Map.Entry<PlayerSession, Board> entry : boards.entrySet()) {
            PlayerSession player = entry.getKey();
            boolean won = player.equals(roundWinner);
            String headline;
            if (won) {
                headline = style.green(Banners.YOU_WIN) + "\nYou guessed it first: " + style.bold(secret);
            } else if (roundWinner != null) {
                headline = style.red(Banners.YOU_LOSE) + "\n" + roundWinner.name() + " guessed it first. The word was "
                        + style.bold(secret) + ".";
            } else {
                headline = style.red(Banners.YOU_LOSE) + "\nNobody guessed it. The word was " + style.bold(secret) + ".";
            }
            player.send("\n" + headline + "\n\n" + PLAY_AGAIN_PROMPT);
        }
        ended.countDown();
    }

    private void broadcastExcept(PlayerSession skip, String message) {
        boards.keySet().stream().filter(p -> !p.equals(skip)).forEach(p -> p.send(message));
    }

    private String render(Board board) {
        String tried = board.tried().isEmpty() ? "-" : board.tried().stream().map(String::valueOf)
                .collect(Collectors.joining(" "));
        return """
                %s

                  %s

                Lives: %d/%d   Tried: %s""".formatted(Gallows.draw(board.misses(), board.maxMisses()),
                style.bold(board.masked()), board.livesLeft(), board.maxMisses(), tried);
    }

    synchronized boolean isFinished() {
        return finished;
    }

    synchronized boolean isOut(PlayerSession player) {
        Board board = boards.get(player);
        return board == null || board.isOver();
    }

    synchronized Optional<PlayerSession> winner() {
        return Optional.ofNullable(winner);
    }

    String secret() {
        return secret;
    }

    int number() {
        return number;
    }

    /** Blocks until the round ends. Returns false on timeout. */
    boolean awaitEnd(long timeout, TimeUnit unit) throws InterruptedException {
        return ended.await(timeout, unit);
    }
}
