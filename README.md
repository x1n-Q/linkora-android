# Linkora for Android

Lightweight Android companion for Linkora, built with Kotlin and Jetpack Compose.

## What works

- Real static-site workspaces selected through Android Files
- Real Node.js workspaces selected through Android Files
- Verified headless Node.js 24 LTS Runtime Pack and npm, with no Termux dependency
- Device-tested PHP 8.4 Runtime Pack with PDO SQLite and the built-in web server
- Real PHP workspaces selected through Android Files, with persistent runtime data
- In-app PHP start, restart, stop, preview, logs, uptime, and memory controls
- In-app controls to install packages and start, restart, or stop Node.js projects
- Editable starter files in `Download/LinkoraProjects/linkora-starter`
- Persisted access to any selected folder containing `index.html`
- Local HTTP server at `http://127.0.0.1:8787` that reads project files on every request
- Static-site SPA route fallback and optional custom `404.html` responses
- In-app live preview with manual reload
- Live Node.js uptime, memory use, runtime logs, and foreground controls
- Persistent, isolated runtime storage for every selected Node.js project
- Built-in SQLite plus `better-sqlite3` and `sqlite3` compatibility adapters
- Browser-based device authorization through Android Custom Tabs
- Automatic approval polling and session restoration
- Android Keystore-encrypted access-token storage
- Standalone mode with no Linkora account or `linkora.top` dependency
- Device-only and local-network project access
- User-owned domain publishing through a Cloudflare Tunnel token
- Selectable Node.js and PHP start commands with `{HOST}` and `{PORT}` placeholders
- Linkora domain-pool and managed-hostname integration
- New or existing Linkora URL assignment through the production API
- Optional Linkora Connector Pack with foreground status and automatic reconnect
- In-app Runtime Manager with background downloads, progress, verification, and install approval
- Read-only MariaDB explorer with table previews and guarded SQL queries
- CPU-neutral main APK with architecture-specific runtime selection
- Linkora brand colors and Android launcher icon

Linkora-managed mode uses Linkora's one-time device authorization flow. The app opens the
approval page on `linkora.top`, never receives the account password, and exchanges
the approved request for a revocable device token.

Standalone mode does not require a Linkora account and does not call the Linkora
API. Projects can stay device-only, be shared on the local network, or use a
Cloudflare Tunnel configured by the user for their own domain.

## Project workflow

1. Select **Linkora managed** or **Standalone** publishing.
2. Select **Static site**, **Node.js**, or **PHP**. Sign in only in Linkora-managed mode.
3. Use the included starter or tap **Choose project folder**.
4. In Android Files, choose a folder containing `index.html` for a static site,
   `package.json` and a supported JavaScript entry file for Node.js, or
   `index.php` for PHP, or a Laravel project containing `artisan` and `public/index.php`.
5. Edit the selected files with any Android file editor.
6. For Node.js, install dependencies if needed and start the runtime. For PHP,
   start the verified PHP Runtime Pack. Open the preview after it is running.
7. Use the detected start command or set a `node`, `npm`, or `php` command.
8. In managed mode, assign a Linkora URL. In standalone mode, keep the project
   local, share it on the LAN, or connect a user-owned Cloudflare tunnel.

The starter in Downloads or the user-selected folder remains the source of
truth. Static projects are served directly. Node.js source is synchronized into
a private workspace whenever the runtime starts or restarts. Every selected
project has separate persistent runtime storage, so generated databases,
uploads, logs, and `node_modules` survive restarts without changing the source
folder.
The main app and static hosting work across Android CPU architectures. Current
Node.js, PHP, MariaDB, and connector binaries are published for ARM64. The
Runtime Manager resolves packs against the device ABI and never offers an
incompatible package. Additional ABI builds can be registered in a main-app
update after their payload hashes and device tests are complete. Python remains
planned.

PHP projects must contain `index.php`, or Laravel's `artisan` and
`public/index.php`. Linkora synchronizes selected project files into a private
runtime workspace before each start or restart. Files created at runtime,
including SQLite databases and uploads, remain available between restarts
without modifying the selected source folder.

Node.js servers should listen on `process.env.HOST` and `process.env.PORT`.
Linkora supplies both values and supports `{HOST}` and `{PORT}` placeholders in
custom start commands. Local-network sharing changes the supplied host from
`127.0.0.1` to `0.0.0.0`; the local port remains `8787`.

The Android Node.js runtime is experimental. Pure JavaScript packages are the
supported baseline. Linkora adapts common `better-sqlite3` and `sqlite3` APIs to
Node's built-in SQLite module inside the private runtime copy. Other packages
containing native code or install scripts, such as `sharp` and native `bcrypt`,
may not install or run. See
[`docs/sqlite-compatibility.md`](docs/sqlite-compatibility.md) for the supported
SQLite surface and limitations.

## Build requirements

- Android SDK 37 and Build Tools 36.0.0
- JDK 17
- Gradle 9.6 through the included wrapper configuration

To reproduce the runtime binaries, follow
[`docs/node-runtime-build.md`](docs/node-runtime-build.md) and
[`docs/php-runtime-build.md`](docs/php-runtime-build.md). Run
`./gradlew :app:assembleDebug :runtime-node:assembleDebug :runtime-php:assembleDebug :runtime-mariadb:assembleDebug :runtime-connector:assembleDebug`
and install the main APK plus the runtime packs required by the project.
Runtime-pack design and verification are documented in
[`docs/runtime-packs.md`](docs/runtime-packs.md). Android Studio is optional.

## F-Droid preparation

F-Droid metadata is maintained in `.fdroid.yml` and `fastlane/metadata/android`.
The main-app recipe removes all prebuilt runtime payloads and excludes the
runtime-pack modules before building the architecture-neutral APK from source.
Static hosting remains fully functional; native runtimes are separate FOSS
applications with their own release and review process. See
[`docs/fdroid-release.md`](docs/fdroid-release.md) for the release checklist.
