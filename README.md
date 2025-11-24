# ChessKel - Mobile Chess Application

![ChessKel Logo](https://github.com/Kelunie/Desarrollo_de_Aplicaciones_para_Dispositivos_M-viles/blob/main/Semana_4/505c07b1-38f0-4a5f-bc38-ca62de98bafe.jpeg)

## Project Overview

**Course:** Mobile Application Development  
**Project:** ChessKel  
**Author / Student:** Ing. Caleb Rodríguez

### Short description
A complete chess application for Android that allows users to create profiles, register their location, and play matches against a local AI or an opponent on the same LAN. The app manages user and match information locally using SQLite and includes lessons, PvP networking, and a move history UI.

---

## Table of contents
- Introduction
- Features
- How it is built
- Project structure and key files
- Important resources and assets
- Build & run (Windows / PowerShell)
- How to test main features
- PvP debugging notes (known issues)
- Functional contract & edge cases
- Notable changes since the original README
- Recommended next steps
- Contributing
- License

---

## Introduction

ChessKel is an Android chess app written in Kotlin that provides:
- Play vs AI (multiple modes),
- Local PvP over LAN (host / join),
- Lessons with YouTube links,
- User profiles with optional geolocation,
- Move history with a RecyclerView and audio feedback,
- Light/dark theme support and consistent UI styling.

The app primarily uses Android Views and AndroidX components. It focuses on keeping logic modular (game engine, UI, persistence, networking).

## Features

- Single-player vs AI (random/basic or Minimax-based modes).
- Local multiplayer (PvP) via sockets on the same local network.
- Lessons with embedded YouTube links and lesson details.
- Profile creation, editing and local persistence (SQLite).
- Move history shown using a `RecyclerView` with an adapter (`MovesAdapter`).
- Sound effects for move, capture, check, and game over (`SoundManager`).
- Theme persistence and utilities (`ThemeUtils`).
- Donation menu entry (PayPal) with a vector icon showing the `$` symbol (`ic_donations.xml`).

## How it is built

The project follows a modular layered architecture for maintainability:

- UI / Screens: Activities and layouts (menus, profile, game board, PvP screens).
- Domain / Game: Chess logic, rules, move generation and validation.
- Persistence: SQLite (`DBHelper` style helpers) for users and game history.
- Communication / Networking: Simple TCP socket client/server for LAN PvP.

Core technologies: Kotlin, Android SDK (View system), AndroidX, RecyclerView.

## Project structure and key files

Important packages and files (paths relative to `app/src/main`):

- `java/com/chesskel/ui/menu/MainMenuActivity.kt` — main menu activity.
- `java/com/chesskel/ui/game/GameActivity.kt` — local game (AI / single device).
- `java/com/chesskel/ui/pvp/PvpLobbyActivity.kt` — PvP lobby (host / join).
- `java/com/chesskel/ui/pvp/PvpGameActivity.kt` — PvP game activity (send/receive moves).
- `java/com/chesskel/ui/profile/ProfileActivity.kt` — profile screen.
- `java/com/chesskel/ui/learn/LessonDetailActivity.kt` — lesson details and videos.
- `java/com/chesskel/ui/game/MovesAdapter.kt` — `RecyclerView` adapter that displays move history.
- `java/com/chesskel/game/` — chess engine, move models and game events (e.g. `ChessEngine`, `Move`, `GameResult`).
- `java/com/chesskel/ui/game/ChessBoardView.kt` — custom board view that draws the board and pieces and handles gestures.
- `java/com/chesskel/util/SoundManager.kt` — audio handling for game events.
- `java/com/chesskel/ui/theme/ThemeUtils.kt` — theme utilities and persistence.

Layouts and resources:

- `res/layout/activity_main_menu.xml` — main menu layout (includes "Donate via PayPal").
- `res/layout/activity_game.xml`, `res/layout/activity_pvp_game.xml` — game screens.
- `res/layout/activity_pvp_lobby.xml` — PvP lobby.
- `res/layout/item_move.xml` — layout for each row in the move list (used by `MovesAdapter`).
- `res/layout/centered_container.xml` — container used to center screen contents (`gravity="center"`).
- `res/drawable/ic_donations.xml` — donation icon (vector showing `$`) with color that adapts to light/dark themes when necessary.
- `res/drawable/bg_btn_primary*.xml` — button background selectors (normal/pressed/disabled).

## Important resources and assets

- `ic_donations.xml` — vector drawable updated to a visible `$` symbol for both light and dark themes. Check `res/drawable` and `res/drawable-night` if the icon does not appear correctly.
- `item_move.xml` + `MovesAdapter` — controls display of moves. The adapter currently truncates or constrains move text so each section fits the UI (requirement: 6-character limit per section). Also the numbering column uses fixed spacing.
- `centered_container.xml` — wrapper layout to center screens with `gravity="center"`.

## Build & run (Windows / PowerShell)

Requirements:
- Android Studio (recommended) or command-line Gradle setup,
- JDK 11+,
- Android SDK matching the project's `compileSdk`.

From project root (PowerShell):

```powershell
# Build debug APK
.\gradlew assembleDebug

# Install debug APK on a connected device or running emulator
.\gradlew installDebug
```

You can also open the project in Android Studio and Run from the IDE.

## How to test main features

- Main menu: open app and choose Play vs AI, PvP, Learn, or Profile.
- Donate: check the "Donate via PayPal" row in the main menu; the icon should show a `$` symbol. If invisible, confirm `res/drawable/ic_donations.xml` and `-night` variants exist.
- Local game (AI): confirm moves update on the board and the moves list fills as the game progresses.
- PvP: host on one device and join from another on the same LAN. Make moves and confirm the other device receives and applies them.
- Lessons: open a lesson and tap to open video links (YouTube).

## PvP debugging notes (known issue)

Reported symptom: "In PvP I make the first move but it doesn't show on the other device; however, resign is recognized on the other device."

Possible causes and diagnostic steps:
1. Network connectivity: ensure both devices are on the same Wi‑Fi and reachable (same subnet). Test connectivity with `ping` or simple socket test.
2. Logging: add `Log.d` statements in `PvpGameActivity.kt` where moves are sent and where incoming moves are handled. Compare logs for `move` vs `resign` handling since `resign` reaches the peer.
3. Payload format: log the exact JSON/string payload sent and received (for example `{ "from":"e2", "to":"e4", ... }`). A malformed payload may be ignored by the parser.
4. Threading / UI update: ensure received moves are applied on the UI thread (use `runOnUiThread { ... }`). If the receiver doesn't apply the move because it believes it's not the receiver's turn, verify turn/state logic.
5. ACK / confirmation: implement a simple ACK message for moves so the sender can know the peer received and applied the move. This helps differentiate between "not received" and "received but not applied".

Quick logging tip (Android Studio / adb):

```powershell
# Filter logs for PvP activity and project tags
adb logcat -s PvpGameActivity:* Chesskel:*
```

## Functional contract & edge cases

Contract (brief):
- Inputs: moves (origin/destination, promotion info), UI commands (resign, undo), mode selection (AI/PvP).
- Outputs: board state updates, moves list (RecyclerView), game end messages, audio events.
- Error modes: malformed move payloads, network disconnects, inconsistent game state.

Common edge cases to test:
- Pawn promotion handling,
- Castling (king/rook moves),
- Ambiguous moves and disambiguation in notation,
- Very long move history (now handled by RecyclerView for performance).

## Notable changes since the original README

- UI: screens and content were standardized to be centered using `centered_container.xml` and `gravity="center"`.
- Buttons: primary button drawables were added/updated (`bg_btn_primary*`).
- Donation icon: replaced with a vector `$` icon to improve visibility in light/dark themes (`ic_donations.xml`).
- Moves list: migrated to `RecyclerView` with `MovesAdapter` to fix bugs when move count grows large.
- Login/register visuals: improved contrast for text and buttons to enhance readability.

## Recommended next steps

- Add unit tests for the chess engine (`ChessEngine`) covering move legality, check/checkmate and draws.
- Improve PvP synchronization: add ACKs, structured logs, payload validation and retry logic.
- Accessibility improvements: text sizes, contrast checks and content descriptions for important icons (e.g. donation icon).
- Add screenshots to README to help contributors and testers.
- Add linting/formatting (ktlint) and a simple CI pipeline.

## Contributing

1. Fork the repository.
2. Create a branch with `feature/` or `fix/` prefix.
3. Run builds and tests locally before making a pull request.
4. Describe changes and reproduction steps in the PR.

## License

See `LICENSE.txt` at the project root and keep consistency with that file.

---
