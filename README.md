# ChessKel - Mobile Chess Application

![ChessKel Logo](https://github.com/Kelunie/Desarrollo_de_Aplicaciones_para_Dispositivos_M-viles/blob/main/Semana_4/505c07b1-38f0-4a5f-bc38-ca62de98bafe.jpeg)

## Project Overview

**Course:** Mobile Application Development  
**Project:** ChessKel  
**Student:** Ing. Caleb Rodríguez

### Brief Description
A complete chess application for Android that allows users to create profiles, register their location, and play matches against a local AI or an opponent on the same LAN, managing all user and match information through CRUD operations with SQLite.

---

## Table of Contents
- [Introduction](#introduction)
- [What the Application Does](#what-the-application-does)
- [How is it Built?](#how-is-it-built)
- [Development Architecture](#development-of-the-chesskel-project)
- [Key Utilities](#key-utilities)

---

## Introduction

Hello! This is the ChessKel project, a chess application built for Android phones using the Kotlin language.

Our goal was to create a complete chess app that goes beyond just moving pieces. We want people to be able to play, register, and save their progress.

## What the Application Does

### Your Profile and Location
- Create an account with your name, password, and profile photo
- Automatic country and region registration during signup using geolocation

### Flexible Gameplay
- **Against the Computer (AI Bot):** Automatic opponent with basic random moves or advanced Minimax algorithm
- **Against a Friend (LAN):** Connect and play with others on the same Wi-Fi network using Socket technology

### Memory and History
- **SQLite Database:** Local storage for all user data and game history
- Save and update user information and profile photos
- Record all played games for historical review

## How is it Built?

The application follows a layered architecture (like a sandwich 🥪) for maintainability:

| Layer | Purpose |
|-------|---------|
| **Screens (UI)** | User interfaces (Menus, Chessboard, Login screen) |
| **Logic (Domain)** | Chess rules and Bot intelligence |
| **Data (SQLite)** | Database operations and persistence |
| **Communication** | Multiplayer networking and geolocation |

ChessKel demonstrates how to combine complex functions like geolocation, multimedia, local networking, and AI into a single functional mobile application.

---

## Development of the ChessKel Project

Built with modular four-layer architecture (UI, Domain, Data, Communication) using Kotlin and SQLite for local data persistence.

### 1. Architecture and Data Persistence (SQLite and Accounts)

Fully local user and game management without cloud dependencies.

#### 1.1 Local Account Management and Authentication

- **Creation and Security:** Passwords are immediately hashed during registration (`RegisterActivity`) and only the hash is stored in SQLite
- **Login Mechanism:** `LoginActivity` hashes entered passwords and compares with stored hash
- **Session Maintenance:** User ID stored in `Preferences.kt` to maintain active sessions between app restarts

#### 1.2 Structure and CRUD with SQLite

Data layer managed using DAO pattern with `DBHelper.kt` handling all CRUD operations:

| Table | Purpose | CRUD Operations |
|-------|---------|-----------------|
| **usuarios** (users) | Store profile data (name, email, passwordHash, location, profile picture) | **Create** (Registration), **Read** (Load Profile), **Update** (Edit Profile), **Delete** (Delete Account) |
| **partidas** (games) | Store game history with player IDs and game state (FEN notation) | **Create** (New Game), **Read** (Load History), **Update** (Resume/Finish Game), **Delete** (Clear History) |

### 2. Domain Logic (The "Brain" of the Game)

Core chess functionality resides in the Domain layer:

- **`Tablero.kt` (Board.kt):** Central 8x8 board model tracking pieces (`Pieza.kt`), using FEN notation for serialization/deserialization
- **`AjedrezController.kt` (ChessController.kt):** Rules engine for:
    - Move validation (`mover(m: Movimiento)`)
    - Check, Checkmate, and Stalemate detection
    - Database synchronization with `DBHelper`
- **`BotIA.kt` (AI Bot.kt):** Automatic opponent with:
    - Basic random move selection
    - Advanced Minimax algorithm for optimal moves

### 3. Communication Layer (LAN Multiplayer)

Temporary Client-Server model using TCP Sockets for same-network gameplay:

| Component | Role | Task |
|-----------|------|------|
| **Server Phone** | Executes `SocketServer.kt` - opens port and waits for connection | Sends/receives moves and manages network session |
| **Client Phone** | Executes `SocketClient.kt` - connects to server's local IP | Sends/receives moves to/from server |

#### Movement Exchange Protocol
- Communication centers on `Movimiento.kt` (Move.kt) objects
- Moves serialized to JSON format: `{"from":"e2","to":"e4", ...}`
- Receiving phone deserializes and applies to local `Board`
- Ensures both devices remain synchronized

## 4. Key Utilities

### Geolocation (`LocationUtils.kt`)
- Uses `FusedLocationProviderClient` for latitude/longitude coordinates
- Employs `Geocoder` to convert coordinates to country/region information
- Stores location data in user profile

### Image Handling (`ImageUtils.kt`)
- Profile picture selection via `Intent`
- Stores local file path in `foto_perfil` database field
- Loads and displays images in `ProfileFragment` using stored paths