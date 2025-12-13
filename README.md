# ChessKelu — Android Chess App

## Overview

ChessKelu is an Android chess application developed as part of my coursework. The app supports local gameplay, player versus player (PVP) over a local network, and includes a learning section. It also features a minimal Express API, deployed on Azure, to persist user profiles. This README provides an overview of the project structure, key files, setup instructions, and known issues.

---

## Quick Summary

- **Platform**: Android (Kotlin)
- **App Package**: `com.chesskel`
- **Main Features**: 
  - Single-player chess against an AI
  - PVP over LAN
  - Learning lessons
  - User authentication and profiles
  - Profile image support
  - Moves history list
  - Donation link in the main menu
- **Supporting API**: Minimal Express API for storing user metadata and profile image URIs (deployed on Azure)

---

## Getting Started

### Android App

To run the Android app, follow these steps:

1. Open the project in Android Studio using the `build.gradle.kts` file located at the repository root.
2. Build and run the project on an emulator or a physical device with the Android SDK installed.

**Note**: The project is configured with Gradle Kotlin DSL (`*.kts`). Use the standard Android run configuration in Android Studio to launch the app.

### API (Local / Azure)

The application is supported by a small Express API. Instructions to run the API locally are as follows:

1. Navigate to the API folder (e.g., `d:\apiAzure`) in your terminal.
2. Run the following commands:

   ```bash
   npm install
   npm run dev
   ```

The API endpoints used by the app include:

- `GET /health`
- `POST /users` (create user)
- `POST /auth/login` (login)
- `PATCH /users/:id/profile` (update profile)
- `PATCH /users/by-email/:email/profile` (upsert profile by email)

The upsert functionality allows the app to save profile data even if the user does not exist on the server.

---

## Architecture and Design Decisions

The app's architecture is designed to separate concerns and facilitate testing:

- **Game Logic**: Centralized in the `ChessEngine` class, which handles rules, move validation, and board state. This separation allows both the UI and network layers to utilize the same engine.
- **Network Code**: Isolated in the `net/*` files, including `ApiClient.kt` for HTTP calls to the Express API, and LAN multiplayer code (`LanSession.kt`, `UdpDiscovery.kt`).
- **UI Structure**: Follows typical Android Activities for different screens (login, register, profile, main menu, game, PVP). A `CenteredActivity` helper is used to center content across activities.
- **Moves List**: Implemented with a `RecyclerView` via `MovesAdapter` to handle large move lists efficiently and avoid UI unresponsiveness.
- **Profile Image Handling**: Uses Android content URIs with support for images from the gallery (content provider) and file manager.
- **Local Persistence**: Managed by a small helper (`DHelper.kt`) for app-specific data storage, with a minimal `User` data class representing user information.

---

## Important Files

### Root-level Files

- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Standard Gradle configuration for the Android project.
- `README.md`: This README file, explaining the project structure and setup.

### App Module (`app/`)

- `app/src/main/java/com/chesskel/game/ChessEngine.kt`: Core chess logic, including board representation, move generation, and turn handling.
- `app/src/main/java/com/chesskel/game/GameActivity.kt`: Activity for single-player games, connecting the UI with `ChessEngine`.
- `app/src/main/java/com/chesskel/ui/pvp/PvpGameActivity.kt`: Activity for PVP games over LAN, integrating LAN session code with `ChessEngine`.
- `app/src/main/java/com/chesskel/ui/game/MovesAdapter.kt`: RecyclerView adapter for rendering the list of moves.
- `app/src/main/res/layout/item_move.xml`: Layout for a single move's row in the RecyclerView.
- `app/src/main/java/com/chesskel/ui/auth/LoginActivity.kt`: Login screen, validating input and contacting the API.
- `app/src/main/java/com/chesskel/ui/auth/RegisterActivity.kt`: Registration screen, creating a local account and posting to the API.
- `app/src/main/java/com/chesskel/ui/profile/ProfileActivity.kt`: Profile screen for changing display name, location, and profile image.
- `app/src/main/java/com/chesskel/net/ApiClient.kt`: HTTP client wrapper for API communication (login, user creation, profile update).
- `app/src/main/java/com/chesskel/net/LanSession.kt` and `app/src/main/java/com/chesskel/net/UdpDiscovery.kt`: LAN multiplayer implementation for discovering peers and managing sessions.
- `app/src/main/java/com/chesskel/data/DHelper.kt`: Data helper for local persistence (SQLite/SharedPreferences wrapper).
- `app/src/main/java/com/chesskel/data/User.kt`: User data class mapping to the API user structure.
- `app/src/main/java/com/chesskel/ui/learn/LessonDetailActivity.kt`: Learning module component for displaying lessons and examples.
- `app/src/main/java/com/chesskel/ui/theme/CenteredActivity.kt`: Base activity with a layout configured to center content.

### Resources (`res/`)

- `res/layout/*.xml`: Layout files for each Activity, using consistent styles and primary button background drawables.
- `res/drawable/bg_btn_primary_*.xml`: Button drawables for different states (normal, pressed, disabled).
- `res/values/strings.xml` and `res/values-en/strings.xml`: String resources, containing both English and default Spanish strings.

---

## API Server Files

The Express API, crucial for persisting user profile data, is minimal and straightforward. The main server file is `server.js`.

Key API functions include:

- **Create User** (`POST /users`): Registers users in the JSON store, allowing persistent IDs.
- **Login** (`POST /auth/login`): Checks stored `passwordHash` for user authentication.
- **Get by Email / ID**: Lookup endpoints used by the app.
- **Upsert Profile by Email** (`PATCH /users/by-email/:email/profile`): Saves profile updates, creating the user if they do not exist.

**Note**: JSON file storage was chosen for its simplicity and ease of deployment to Azure. A real-world application would require a database and proper authentication mechanisms.

---

## Design Choices and Trade-offs

As a student project, several design choices were made for simplicity and educational purposes:

- **Single Engine File**: Simplifies the project structure but may not scale well for larger projects.
- **Minimal Authentication**: Suitable for demo purposes but not secure for production use.
- **Gallery vs. File Picker**: Content URI handling was added to support profile image selection from the gallery, addressing Android's return of content URIs.
- **RecyclerView for Moves**: Prevents performance issues and UI freezes with long move lists.
- **Centered Layout Base**: The `CenteredActivity` class ensures consistent and easy centering of all screens.

---

## Known Issues and TODOs

- **PVP Synchronization**: The first move is not always reflected on the other device. Review the `LanSession` send/receive acknowledgments and retry logic.
- **Profile Image Crash**: If the saved profile image URI points to a removed file path, the profile load may crash. Implement robust content URI handling and try/catch around image loading.
- **Improve Authentication**: Replace plain `passwordHash` comparison with JWT-based authentication and secure password storage.
- **Automated Tests**: Add unit tests for `ChessEngine` and integration tests for the API client.

---

## Grading Criteria (For Instructor)

The app can be evaluated based on the following criteria:

- **Functionality**: Does the app run? Can users play single-player and PVP? Are moves processed correctly and persisted?
- **Code Structure**: Is the game logic separated from the UI and network code? Clear package organization and single-responsibility principles should be evident.
- **Network & API Usage**: Does the profile save/load functionality work with the provided API?
- **UX & Polish**: Are screens properly centered? Is there a consistent button style? Does profile image selection work from the gallery? Is RecyclerView used for moves?

---

## Contact / Credits

This project was developed as coursework. For any clarifications or focus on specific parts (e.g., PVP sync, profile image fixes, README language polish), please reach out.

**License**: MIT (see LICENSE.txt)

---