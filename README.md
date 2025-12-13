# ChessKel — Android Chess App

A lightweight Android chess app with local gameplay (AI), LAN PvP, lessons, and simple user profiles. This README is updated to reflect the current state of the project and its integration with a minimal Express API hosted in Azure.

## Table of contents
- Project summary
- What changed (short)
- App architecture (high level)
- Azure API (endpoints and behavior)
- Profile sync behavior (what the app does)
- Known limitations (images, remote ids)
- Build & run (Android app)
- Quick API test commands (PowerShell)
- How to verify profile sync from the app
- Troubleshooting & debugging tips
- Next steps / recommendations

---

## Project summary

ChessKel is an Android chess application written in Kotlin. It includes:
- Single-player vs AI (multiple difficulty options)
- LAN PvP (host / join) using sockets
- Lessons with video links
- User profiles stored locally (SQLite) and optionally synced with a remote API
- Move history using a RecyclerView (MovesAdapter)
- Light/dark theme and basic styling

This repository contains the Android app. A minimal Express API (separate project) is used to persist user profiles remotely; that API is deployed on Azure and can be used by the app.

---

## What changed since the original README
- The app now integrates with a remote API hosted on Azure for user/profile sync.
- `ProfileActivity` persists and syncs profile data with the API using an "upsert by email" endpoint (`PATCH /users/by-email/:email/profile`).
- Image selection from the gallery now uses the system picker (ACTION_OPEN_DOCUMENT) and attempts to persist URI permissions so gallery images don't crash the app on reload.
- Profile loading now respects the logged-in user (reads `current_user_id` and `current_user_email` from SharedPreferences) so the Profile screen shows the active user's data instead of always showing the first DB row.
- Donation icon updated to use a visible `$` vector drawable (light/dark aware).
- The moves list was migrated to RecyclerView to avoid UI issues when the move history grows.

---

## App architecture (high level)
- UI: Activities and XML layouts (menu, profile, game, PvP, lessons).
- Game: `ChessEngine` and related classes perform rules, move generation and validation.
- Persistence: local SQLite via `DBHelper` (file: `app/src/main/java/com/chesskel/data/DHelper.kt`).
- Networking (app->server): small HTTP client in `ApiClient.kt` calling the Azure API.
- PvP: local LAN sockets (see `LanSession` and `PvpGameActivity`).

Key Android files:
- `ProfileActivity.kt` — profile UI and save/sync logic
- `LoginActivity.kt` / `RegisterActivity.kt` — auth flows (local + remote attempts)
- `ApiClient.kt` — small HttpURLConnection-based client used to call the Azure API

---

## Azure API (deployed endpoint)
Base URL (example Azure deployment used while developing):

https://chesskelu-g4dsgafjefe6fugv.canadacentral-01.azurewebsites.net

Endpoints (server behavior):
- GET /health — health check
- POST /users — create user
  - body: `{ "nombre": string, "email": string, "passwordHash": string }`
  - returns 201 + user shape
- GET /users/:id — get user by id
- GET /users/by-email/:email — get user by email
- PATCH /users/:id/profile — update profile fields for an id
- PATCH /users/by-email/:email/profile — upsert profile by email: if user exists it updates, otherwise it creates a minimal user and applies the profile fields
- POST /auth/login — login using email + passwordHash (returns user shape if match)

Server response shape (used by the app):
```
{
  "id": 1,
  "name": "Nombre",
  "email": "email@example.com",
  "profileImageUri": null,
  "location": "Country / State / City"
}
```

Notes about the upsert endpoint:
- The app calls `PATCH /users/by-email/:email/profile` with a JSON body containing `profileImageUri` and `location` (and optionally `nombre` / `passwordHash` for creation).
- The server will respond with 200 (updated) or 201 (created + profile applied).

---

## Profile sync behavior (what the app does)
When the user taps "Save changes" in the Profile screen:
1. The app saves profileImageUri and location locally in SQLite (via `DBHelper.updateUserProfile`).
2. The app calls the remote upsert endpoint: `PATCH /users/by-email/{email}/profile`.
   - If the user exists on the server the server updates the profile and returns 200.
   - If the user does not exist, the server creates a minimal user and then applies the profile and returns 201.
3. The app shows a Toast with the result (synced or failed). The local DB always stores the changes regardless of network outcome.

Important: the app sends the image as a URI string (for example: `content://...` or a FileProvider URI). The server only stores this string — it does not receive the image binary. See "Known limitations" below.

---

## Known limitations and important notes
- Image sync: the app currently sends the profile image as a URI string. If the URI is a device-local content:// URI, the server cannot access the binary data or serve it to other clients. To make profile pictures available across devices you need:
  - an upload endpoint on the server that accepts multi-part file uploads and stores the file in shared storage (e.g. Azure Blob Storage), or
  - the app to upload the image to a storage service and send the resulting public URL to the server as `profileImageUri`.

- Remote id: the app uses email-based upsert for robustness and does not store the remote numeric id in the local DB. If you want quicker syncs and fewer lookups, consider storing the server id locally (and updating it at registration/login time).

- Concurrency/race: the upsert endpoint handles a race where two clients try to create the same email by catching UNIQUE errors and re-applying the update.

- Permissions: gallery URIs require persisted URI permission (the app now attempts to call `takePersistableUriPermission`). On some pickers this is not available — the app catches that and still shows the selected image for the session.

---

## Build & run (Android app)
Requirements:
- Android Studio (recommended) or command-line Gradle
- JDK 11+
- Android SDK matching the project's compile/target SDK

From the project root (Windows / PowerShell):

```powershell
# Build debug APK
.\gradlew assembleDebug

# Install debug APK on a connected device or emulator
.\gradlew installDebug
```

Open the project in Android Studio for debugging and Logcat output.

---

## Quick API test commands (PowerShell)
Replace `<your-app>` with your actual Azure host if different.

- Health check:
```powershell
Invoke-RestMethod -Uri "https://chesskelu-g4dsgafjefe6fugv.canadacentral-01.azurewebsites.net/health" -Method GET
```

- Create user (example):
```powershell
Invoke-RestMethod -Uri "https://chesskelu-g4dsgafjefe6fugv.canadacentral-01.azurewebsites.net/users" -Method POST -ContentType "application/json" -Body (@{
  nombre = "Pepe"
  email = "test02@example.com"
  passwordHash = "HASH_FROM_APP"
} | ConvertTo-Json)
```

- Upsert profile by email (create or update profile):
```powershell
$body = @{
  profileImageUri = "content://some/uri"
  location = "Costa Rica / San José / Cantón"
  nombre = "Pepe"            # optional (used if creating)
  passwordHash = ""         # optional
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://chesskelu-g4dsgafjefe6fugv.canadacentral-01.azurewebsites.net/users/by-email/test02@example.com/profile" -Method PATCH -ContentType "application/json" -Body $body
```

- Get user by email (verify profile fields):
```powershell
Invoke-RestMethod -Uri "https://chesskelu-g4dsgafjefe6fugv.canadacentral-01.azurewebsites.net/users/by-email/test02@example.com" -Method GET
```

---

## How to verify profile sync from the app
1. Create a user in the app (Register) or login.
2. Open Profile, choose an image from the gallery and set a location.
3. Tap "Save changes": the app will show a Toast saying it saved locally and another Toast for the remote sync result.
4. Use the GET /users/by-email/:email endpoint (PowerShell example above) to confirm `profileImageUri` and `location` changed on the server.

If the GET shows the new location and the `profileImageUri` string you sent, the upsert worked. Remember the `profileImageUri` is a reference string — the server does not host the image itself unless you implement file upload.

---

## Troubleshooting & debugging tips
- If Profile shows the wrong user after registering a new account, confirm that `current_user_id` and `current_user_email` were written to SharedPreferences. You can add temporary `Log.d` statements in `ProfileActivity.loadUser()` to inspect values.
- If the app crashes when opening the saved gallery image after restart, confirm the picker grants persistable URI permission; otherwise reselect the image and the app will store a usable URI for the session.
- For PvP issues: compare logs on both devices and inspect the payload format and acknowledgements. The app includes `PvpGameActivity` and `LanSession` for reference.

---

## Next steps / recommendations
- Implement an image upload endpoint in the API and make the app upload profile pictures (store and send a public URL).
- Persist the remote user id locally to speed up syncs and reduce lookups.
- Add UI indicators while syncing (progress spinner) and retry logic for failed syncs.
- Add unit tests for `ChessEngine` and integration tests for profile sync.

---

## Contributing
1. Fork the repo and create a branch with `feature/` or `fix/` prefix.
2. Run and test locally before opening a PR.
3. Describe changes clearly and how to reproduce.

---

## License
See `LICENSE.txt` in the project root.

---

If you want, I can also:
- Add screenshots to the README,
- Add an example of how to implement an image upload endpoint (server + client example), or
- Add small tests validating the profile loading logic in `ProfileActivity` and `DBHelper`.
