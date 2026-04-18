# Job 33: J~Net Notes Android App

## Overview
A hybrid Android application for note-taking that provides a seamless experience between offline local storage (SQLite) and online synchronization via the J~Net cloud infrastructure.

## Core Features
- **Offline-First Architecture**: All notes are stored locally in an SQLite database, ensuring the app works without an internet connection.
- **Cloud Sync**: Integration with `https://jnetai.com/apps/Notes/remote-notes.php` for backup, synchronization, and multi-device access.
- **User Authentication**: Secure login using J~Net account credentials.
- **Standalone Capability**: Operates as a full Android app with its own UI, not just a website wrapper.

## Technical Specifications
- **Language**: Kotlin (Modern Android Standard)
- **Local Database**: Room Persistence Library (abstraction over SQLite)
- **Networking**: Retrofit 2 / OkHttp for API communication with PHP backend.
- **UI**: Jetpack Compose for a modern, responsive Material Design interface.

app for android that does both (sqlite for local note storage) with simple password that stores the hash not the actual password and uses password to encrypt and decrypt also that allows remote storage using that link i gave for remote storage and authentication via that site link like i said! dont ask silly quesations just do it

dark themes app but allow it to go light theme from settings aswell! allow export and import

## Data Flow
1. **User creates note** $\rightarrow$ Saved to local SQLite DB.
2. **Sync Triggered** (Manual or Auto) $\rightarrow$ App checks for internet $\rightarrow$ Pushes local changes to `remote-notes.php` $\rightarrow$ Pulls updates from server.
3. **Conflict Resolution**: Latest timestamp wins.

## Folder Structure (Planned)
- `/src/ui`: Compose screens (NoteList, NoteEditor, Settings).
- `/src/data/local`: Room DB entities and DAOs.
- `/src/data/remote`: Retrofit API interfaces and DTOs.
- `/src/repository`: Logic to manage the switch between local and remote data.
- `/docs`: API specifications and database schema.

use github workflows to create the apk to test on android s8 and s9 and must also work with new android versionjs for use with google pixel 6 and 7 aswell

apk folder to store the release /home/jay/Documents/Scripts/AI/openclaw/job33/apk/


dont edit this file

save changes.txt

tell me when apk file is done and ready to test (release it and download it to apk folder)


