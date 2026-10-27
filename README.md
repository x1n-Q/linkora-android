# Linkora for Android

Lightweight Android companion for Linkora, built with Kotlin and Jetpack Compose.

## What works

- Real static-site workspaces selected through Android Files
- Editable starter files in `Download/LinkoraProjects/linkora-starter`
- Persisted access to any selected folder containing `index.html`
- Local HTTP server at `http://127.0.0.1:8787` that reads project files on every request
- In-app live preview with manual reload
- Browser-based device authorization through Android Custom Tabs
- Automatic approval polling and session restoration
- Android Keystore-encrypted access-token storage
- Linkora domain-pool and managed-hostname integration
- New or existing Linkora URL assignment through the production API
- Built-in Cloudflare connector with foreground status and automatic reconnect
- Linkora brand colors and Android launcher icon

Sign-in uses Linkora's one-time device authorization flow. The app opens the
approval page on `linkora.top`, never receives the account password, and exchanges
the approved request for a revocable device token.

## Project workflow

1. Select **Static site** and sign in with Linkora.
2. Use the included starter or tap **Choose project folder**.
3. In Android Files, choose a folder that contains `index.html` and tap
   **Use this folder**.
4. Edit the selected files with any Android file editor.
5. Open **Preview local server** and reload after edits.
6. Tap **Assign Linkora URL** to reserve a hostname or use an existing managed
   hostname.
7. Linkora starts its built-in connector and keeps the phone-hosted project online.

The selected folder remains the source of truth. Linkora does not copy imported
projects into private app storage. The built-in connector currently supports
ARM64 Android phones. Node.js, PHP, and Python runtime support is shown as
planned and is intentionally disabled in this release.

## Build requirements

- Android SDK 37 and Build Tools 36.0.0
- JDK 17
- Gradle 9.6 through the included wrapper configuration

Run `./gradlew :app:assembleDebug` and install the generated APK on an Android 8.0
or newer device. Android Studio is optional.
