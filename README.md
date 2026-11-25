# Hanged Games

[![CI](https://github.com/filipe4fun/Hanged-Games-Next/actions/workflows/ci.yml/badge.svg)](https://github.com/filipe4fun/Hanged-Games-Next/actions/workflows/ci.yml)

A multiplayer hangman you play in the terminal over the network. Start the server, everyone connects with
`nc` or `telnet`, and you all race to guess the same word. The first to crack it wins the round.

It started in 2022 as a weekend project by three bootcamp students at
[Academia de Código](https://academiadecodigo.org/) learning Java sockets and threads. This version rebuilds it
with modern Java, proper concurrency, tests and CI, while keeping the original spirit and ASCII art.

```
=== Round 1 ===
Players: alice, bob
The word has 11 letters. First to guess it wins. You have 6 lives.

  +---+
  |   |
  O   |
  |   |
      |
      |
=========

  _ O _ _ _ _ _ _ _ _ O

Lives: 4/6   Tried: A O U
guess>
```

## Quick start

You need **Java 21 or newer** and Maven.

```bash
mvn package
java -jar target/hanged-games.jar            # waits for 2 players on port 8081
```

Then, from any terminal on the network (one per player):

```bash
nc localhost 8081        # or: telnet localhost 8081
```

### Options

| Option | Default | What it does |
| --- | --- | --- |
| `-p`, `--port <n>` | `8081` | TCP port to listen on |
| `-n`, `--players <n>` | `2` | Players needed to start a round |
| `-l`, `--lives <n>` | `6` | Wrong guesses allowed per player |
| `-w`, `--words <file>` | bundled list | Your own word list, one word or phrase per line |
| `--no-color` | colours on | Plain text for terminals without ANSI support |

`java -jar target/hanged-games.jar 3` still works as in the original: it starts a 3-player game.

## How to play

- Pick a name when you connect. Names must be unique.
- When enough players have joined, a round starts and everyone gets the **same word on their own board**.
- Type a letter to reveal it, or the whole word if you think you know it. A wrong letter or a wrong word costs
  a life. Guesses ignore case and accents, so `e` reveals `É`.
- You see when other players reveal letters or run out of lives, but never their board.
- The first player to complete the word wins. If everyone runs out of lives, nobody does.
- After a round, press Enter to play again or type `/quit` to leave. You can `/quit` at any time.

## How it works

```
src/main/java/io/github/filipe4fun/hangman
├── Main.java              entry point and command line
├── ServerConfig.java      validated options
├── game/                  pure game rules, no networking
│   ├── Board.java         one player's view of the word, lives and guesses
│   ├── GuessResult.java   what a guess did
│   ├── Gallows.java       the drawing, scaled to any number of lives
│   └── WordBank.java      loads and picks words
└── server/                networking and multiplayer flow
    ├── GameServer.java    accepts connections, one virtual thread per player
    ├── Lobby.java         gathers players and starts rounds
    ├── Round.java         the race: shared word, separate boards, one winner
    └── PlayerSession.java talks to one connected player
```

- **Rules are separate from networking.** `game/` has no sockets or threads, so it is easy to test.
- **Virtual threads** (Java 21) give every player a cheap thread of their own; an idle player never blocks
  anyone.
- **Thread safety by design:** each `Round` applies guesses one at a time under its own lock, and the lobby
  hands complete rounds to players through `CompletableFuture`s.
- **The server never stops for a winner.** Rounds end, players go back to the lobby, and new rounds start.

## Tests

```bash
mvn verify
```

Unit tests cover the rules (`BoardTest`, `GallowsTest`, `WordBankTest`, `ServerConfigTest`). `GameServerTest`
starts a real server on a free port and plays full games through real sockets: winning, losing, guessing the
whole word, duplicate names, playing again, players disconnecting mid-round and `/quit`.

GitHub Actions runs the build and tests on Java 21 and 25 for every push and pull request.

## What changed from the 2022 version

The original was a fun first contact with sockets and threads. Reviewing it with fresh eyes:

| Problem in the original | Now |
| --- | --- |
| A win called `System.exit`, shutting the server down for everyone | Rounds end cleanly and players can play again |
| Players joining after the first round waited forever | The lobby starts a new round whenever enough players are ready |
| The player list was a plain `ArrayList` shared by many threads | Shared state lives behind locks and concurrent collections |
| Waiting for players was a `Thread.sleep` busy loop | Players wait on a `CompletableFuture` |
| A disconnect crashed that player's thread with a stack trace, and could stall the game | Disconnects are handled and the round carries on |
| The server printed the secret word in its own console | The word is only revealed when the round ends |
| Unlimited guesses, no gallows | Lives, a gallows drawing and win/lose conditions |
| `é` and `e` counted as different letters | Guesses ignore accents and case |
| A failed bind on the port surfaced as a `NullPointerException` | A clear error message |
| Words were hard-coded in Java (and `words.txt` was unused) | Words load from a file, with comments and de-duplication |
| Depended on a bundled bootcamp `.jar` and IntelliJ project files | Plain Maven build with no runtime dependencies |
| Hard-coded port and player count | Command-line options with validation and `--help` |
| No tests | 37 tests, including end-to-end games over real sockets, run in CI |

## Credits

Originally written by Filipe Costa, Pedro Correia and Pedro Costa during the Academia de Código bootcamp.
The ASCII art banners are from that first version.
