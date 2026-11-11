# Android MariaDB runtime build

Linkora packages MariaDB as a separate, headless Android application. The pack
contains no activity, service, receiver, or background process. Android Package
Manager owns its executable ARM64 files, while the main Linkora app owns the
database directory, foreground service, credentials, logs, and lifecycle.

## Inputs

- MariaDB Community Server `13.0.2`
- Android ARM64 packages maintained by Termux
- minimum Android API `26`
- target ABI `arm64-v8a`

The build script pins and verifies every downloaded package. It retains only
the server, its direct shared libraries, bootstrap SQL, English messages, and
character-set definitions. Dependency names and the server RUNPATH are changed
for Android APK native-library packaging; MariaDB behavior is not modified.

## Build

Install `patchelf`, then run:

```sh
./scripts/build-mariadb-runtime.sh
./gradlew :runtime-mariadb:assembleDebug
./scripts/verify-mariadb-runtime-pack.sh
```

Use `MARIADB_BUILD_WORK_DIR` to preserve package downloads and extracted files.
The resulting APK is
`runtime-mariadb/build/outputs/apk/debug/runtime-mariadb-debug.apk`.

## App lifecycle

Install the runtime pack, open a Node.js or PHP workspace, and use the optional
database panel to start MariaDB. The main app binds it only to
`127.0.0.1:3307`, creates an app-private data directory, generates local
credentials, and keeps the process alive with a foreground service.

Linkora adds these connection values to the project environment:

- `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`
- `MYSQL_HOST`, `MYSQL_TCP_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`

The database panel shows the same values, current RAM use, uptime, and recent
server output. Stopping the project does not delete its database files.

## Distribution

MariaDB Community Server is GPLv2. The runtime APK includes its license,
third-party notices, dependency notices, and a source offer. Keep the build
script and exact package hashes available beside every distributed runtime APK.
