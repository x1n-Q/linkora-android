# Linkora for Android

Lightweight Android companion for Linkora, built with Kotlin and Jetpack Compose.

## What works

- Real static-site workspaces selected through Android Files
- Real Node.js workspaces selected through Android Files
- Bundled ARM64 Node.js 24 LTS runtime and npm, with no Termux dependency
- In-app controls to install packages and start, restart, or stop Node.js projects
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

1. Select **Static site** or **Node.js** and sign in with Linkora.
2. Use the included starter or tap **Choose project folder**.
3. In Android Files, choose a folder containing `index.html` for a static site,
   or `package.json` and a supported JavaScript entry file for Node.js.
4. Edit the selected files with any Android file editor.
5. For Node.js, install dependencies if needed and start the runtime. Open
   **Preview local server** after the project is running.
6. Tap **Assign Linkora URL** to reserve a hostname or use an existing managed
   hostname.
7. Linkora starts its built-in connector and keeps the phone-hosted project online.

The starter in Downloads or the user-selected folder remains the source of
truth. Static projects are served directly. Node.js projects are synchronized
into private app storage whenever the runtime starts or restarts, while
`node_modules` is retained between runs.
The built-in connector and Node.js runtime currently support ARM64 Android
phones. PHP and Python remain planned.

Node.js servers must listen on `process.env.HOST` and `process.env.PORT`.
Linkora sets these to `127.0.0.1` and `8787` before starting the project.

The Android Node.js runtime is experimental. Pure JavaScript packages are the
supported baseline. Packages containing native code or install scripts, such as
`sharp`, native `bcrypt`, and some SQLite packages, may not install or run.

## Build requirements

- Android SDK 37 and Build Tools 36.0.0
- JDK 17
- Gradle 9.6 through the included wrapper configuration

To reproduce the bundled Node.js binary, follow
[`docs/node-runtime-build.md`](docs/node-runtime-build.md). Run
`./gradlew :app:assembleDebug` and install the generated APK on an Android 8.0
or newer ARM64 device. Android Studio is optional.
