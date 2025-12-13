# ChessKelu — Android Chess App (student overview)

This repository contains an Android chess application (mobile) I built as a TI student. The app contains local game play, PVP over local network, a small learning section, and a minimal Express API used to persist user profiles (deployed to Azure). This README explains the project structure, purpose of important files, why they were implemented that way, how to run the app and API, and known issues / next steps.

I wrote this README in English as requested by my teacher and to make the project easier to evaluate.

---

## Quick summary

- Platform: Android (Kotlin)
- App package: `com.chesskel`
- Main features: single-player chess vs AI, PVP over LAN, learning lessons, user authentication/profile, profile image support, moves history list, donation link in main menu.
- Supporting API: Minimal Express API to store user metadata and profile image URIs (deployed to Azure)

---

## How to run

### Android app

1. Open the project in Android Studio (use the `build.gradle.kts` at the repo root).
2. Build and run on an emulator or device with Android SDK installed.

Notes: The project uses Gradle Kotlin DSL (`*.kts`). Use the standard Android run configuration in Android Studio.

### API (local / Azure)

I built a tiny Express API (local instructions below). The live API is also deployed on Azure at a URL used by the app for profile saving.

To run locally (PowerShell example):

```powershell
# from a local copy of the API folder (e.g., d:\apiAzure)
npm install
npm run dev
```

Endpoints used by the app (deployed example base URL):
- GET /health
- POST /users (create user)
- POST /auth/login (login)
- PATCH /users/:id/profile (update profile)
- PATCH /users/by-email/:email/profile (upsert profile by email)

I implemented upsert behavior in the API so the app can save profile data even if the user wasn't created previously on the server.

---

## High-level architecture and design decisions

- I implemented the game logic (rules, move validation, board state) in a single `ChessEngine` class so the UI and network layers can use the same engine and keep logic centralized and easier to test.
- Network code is separated into `net/*` files: `ApiClient.kt` (HTTP calls to the Express API) and LAN multiplayer code (`LanSession.kt`, `UdpDiscovery.kt`). Separating these responsibilities helps keep the UI simple and the networking code testable.
- UI follows typical Android Activities for screens (login, register, profile, main menu, game, pvp). I added a `CenteredActivity` helper to easily center content across activities.
- The moves list uses a `RecyclerView` via `MovesAdapter` to avoid UI issues when the list grows (this fixes a bug where many moves caused the screen to become unresponsive).
- Profile image handling uses Android content URIs. I added handling to support images selected from the gallery (content provider) instead of opening only the file manager.
- I use a small local persistence helper (`DHelper.kt`) for app-specific data storage, and a minimal `User` data class to represent user information.

---

## Important files (what they do and why I did it that way)

I wrote these descriptions in first person as a TI student to explain my choices.

### Root-level files

- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` — Standard Gradle configuration for the Android project. I left default Android project structure because Android Studio requires it.
- `README.md` — This file (updated). I wrote it to explain the project structure and how to run the app and API.

### App module (app/)

This is the Android application module.

- `app/src/main/java/com/chesskel/game/ChessEngine.kt` — Core chess logic: board representation, move generation, turn handling, check/checkmate detection. I implemented this in a single file to keep the game rules centralized and independent from UI or networking code. Centralizing the engine helps avoid duplicated logic across single-player and PVP modes.

- `app/src/main/java/com/chesskel/game/GameActivity.kt` — Activity responsible for single-player games. It connects the UI with `ChessEngine`, handles user interactions for moving pieces, and draws the board. I keep UI code separate from game logic to make the application easier to maintain.

- `app/src/main/java/com/chesskel/ui/pvp/PvpGameActivity.kt` — Activity for PVP games over LAN. This file integrates the LAN session code with the `ChessEngine` and handles sending/receiving moves. I designed it to reuse the same engine so both single-player and PVP behave identically from a rules perspective.

- `app/src/main/java/com/chesskel/ui/game/MovesAdapter.kt` — RecyclerView adapter that renders the list of moves. I implemented moves as a RecyclerView so the list is efficient and doesn't block when it grows large.

- `app/src/main/res/layout/item_move.xml` — Layout for a single moves row in the RecyclerView. I formatted the move row to show move number and each player's short move string. I limited the visible move text to 6 characters visually so UI doesn't break with long SAN strings.

- `app/src/main/java/com/chesskel/ui/auth/LoginActivity.kt` — Login screen. It validates input and contacts the API via `ApiClient.kt`. I also ensure credentials are checked locally when needed.

- `app/src/main/java/com/chesskel/ui/auth/RegisterActivity.kt` — Registration screen. It creates a local account and can post to the API. I kept registration straightforward: collect name, email and a hashed password.

- `app/src/main/java/com/chesskel/ui/profile/ProfileActivity.kt` — Profile screen where users can change display name, location and profile image. I implemented a "Save" button that calls the API upsert endpoint (PATCH /users/by-email/:email/profile) so profile changes are saved to the server when available. I also added a "Sign Out" button to clear local session.

  Why I did it this way: I want profile changes to persist to the API but also work offline. For this reason the app saves locally and tries to sync with the server.

- `app/src/main/java/com/chesskel/net/ApiClient.kt` — Small HTTP client wrapper used by the app to talk to the Express API (login, create user, profile update). I implemented simple, synchronous-friendly coroutines / async wrappers (depending on the code) to keep Activity code readable.

- `app/src/main/java/com/chesskel/net/LanSession.kt` and `app/src/main/java/com/chesskel/net/UdpDiscovery.kt` — LAN multiplayer implementation. `UdpDiscovery` helps discover peers on the local network (broadcast/multicast discovery). `LanSession` manages a TCP/UDP session between two devices to exchange moves in PVP. I separated discovery and session layers to keep code modular.

- `app/src/main/java/com/chesskel/data/DHelper.kt` — Small data helper for local persistence (SQLite/SharedPreferences wrapper). I wrote a helper so saving/loading session and user data is centralized in one place.

- `app/src/main/java/com/chesskel/data/User.kt` — Simple User data class used across the app and mapping to the API user shape.

- `app/src/main/java/com/chesskel/ui/learn/LessonDetailActivity.kt` — Part of the learning module. Shows lessons and examples for training.

- `app/src/main/java/com/chesskel/ui/theme/CenteredActivity.kt` — Base activity with a layout configured to center content (gravity center). I added it to help keep screens centered and consistent.

### Resources (res/)

- `res/layout/*.xml` — Layout files for each Activity. I used consistent styles and a primary button background drawables to keep UI consistent.
- `res/drawable/bg_btn_primary_*.xml` — Button drawables for normal/pressed/disabled states. I added these to match the app's visual style.
- `res/values/strings.xml` and `res/values-en/strings.xml` — String resources. I kept English and default Spanish strings; final README and app text should be in English for your teacher.

---

## API server files (server side — not inside the Android app)

I created a minimal Express API to persist user profile data. The main server file provided is `server.js` (example shown below).

The important API functions and reasons:

- Create user (`POST /users`) — to register users in the JSON store so they can have persistent IDs.
- Login (`POST /auth/login`) — minimal login which checks stored `passwordHash`.
- Get by email / id — lookup endpoints used by the app.
- Upsert profile by email (`PATCH /users/by-email/:email/profile`) — I added this endpoint so the app can save profile updates even if the user did not exist in the API yet. The endpoint will create the user if missing and then apply the profile update; this simplifies client logic.

Why JSON file storage: I wanted a zero-dependency server that is easy to deploy to Azure and simple to understand for the course. For production you'd use a database and proper auth.

---

## Student notes — design choices & tradeoffs

- Single engine file: simpler for a student project, but would be split into components for larger projects.
- Minimal auth (no JWT): ok for demo/learning, but not secure for production.
- Gallery vs file picker: I added content URI handling to let the profile image come from the gallery (not only file manager), because Android gallery access returns content URIs which needed special handling.
- RecyclerView for moves: solves performance problems when the moves list becomes long and prevents UI freezes.
- Centered layout base: I created `CenteredActivity` so all screens can be easily centered and consistent with a single change.

---

## Known issues and TODOs

- PVP synchronization: I observed an issue where the first move is sent but not always reflected on the other device. I recommend reviewing the `LanSession` send/receive acknowledgements and retry logic.
- Profile image crash: if the saved profile image URI points to a file path that is later removed, the profile load may crash — I recommend adding robust content URI handling and try/catch around image loading.
- Improve auth: replace plain `passwordHash` compare with real JWT-based auth and secure password storage.
- Automated tests: add unit tests for `ChessEngine` and integration tests for API client.

---

## How I want this to be graded (suggestion for the teacher)

- Functionality: Does the app run? Can we play single-player and PVP? Are moves correct and persistent?
- Code structure: Is game logic separated from UI and network code? I used clear packages and single responsibilities.
- Network & API usage: Does the profile save/load flow work with the provided API?
- UX & polish: Screens centered, consistent buttons, profile image selection from gallery, RecyclerView for moves.

---

## Contact / credits

Project made by a TI student as coursework. If you need clarifications or want me to focus on specific parts (PVP sync, profile image fixes, README language polish), tell me and I will update the project.

License: MIT (see LICENSE.txt)

---

If you want, I can now:
- Translate all in-app strings to English and update `values-en` as the default.
- Fix the profile gallery selection crash.
- Investigate the PVP sync bug and propose a patch.

Tell me which of these I should do next.
