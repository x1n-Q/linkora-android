# Runtime packs

Linkora can load large native runtimes from separately installed, headless
Android packages. A runtime pack has no launcher activity, service, receiver,
or background process. Android Package Manager installs its native payload as
read-only executable code, while the main Linkora app owns project files,
foreground services, logs, and process lifecycle.

## Package contract

The first contract version requires these application metadata values:

| Key | Node.js | PHP | MariaDB | Connector |
| --- | --- | --- | --- | --- |
| `top.linkora.runtime.ID` | `node` | `php` | `mariadb` | `connector` |
| `top.linkora.runtime.PROTOCOL` | `1` | `1` | `1` | `1` |
| `top.linkora.runtime.VERSION` | `24.22.1` | `8.4.27` | `13.0.2` | `2026.10.4` |
| `top.linkora.runtime.ABI` | `arm64-v8a` | `arm64-v8a` | `arm64-v8a` | `arm64-v8a` |
| `top.linkora.runtime.EXECUTABLE` | `libnode_runtime.so` | `libphp_runtime.so` | `libmariadbd_runtime.so` | `libcloudflared.so` |

The main app declares the known package IDs in its manifest `<queries>` block.
It reads `ApplicationInfo.nativeLibraryDir`, validates the contract metadata,
checks every native payload and asset against hashes pinned in the main app,
and confirms that the executable has Package Manager-provided execute access.
Package names and metadata alone are never treated as proof of integrity.

The pack's signing certificate is also compared with the main app certificate.
A matching certificate is recorded as stronger provenance, while pinned payload
hashes keep independently signed store builds verifiable.

## Runtime Manager

The main application is CPU-neutral. It uses Android Download Manager for
visible, resumable background downloads and keeps the active download IDs in
app preferences. The Runtime Manager shows live progress, verifies the package
identity, contract metadata, ABI, and every pinned payload hash, then opens the
Android package installer. Android always asks the user before installation.

Runtime releases are resolved in `Build.SUPPORTED_ABIS` order. Each release
asset uses this name:

```text
linkora-runtime-<runtime-id>-<android-abi>.apk
```

For example, the current Node.js ARM64 asset is
`linkora-runtime-node-arm64-v8a.apk`. Release assets are downloaded from the
latest GitHub release. A private repository or missing asset returns a clear
retryable download error.

Current native payloads are ARM64. An x86_64, x86, or ARMv7 entry must not be
added to the catalog until the matching native build, manifest ABI, pinned
hashes, and physical-device or emulator tests exist. Unsupported devices can
still install Linkora and host static projects.

## Node.js pack

`runtime-node` is the reference pack. It contains Node.js, the Android NDK C++
runtime, npm, and their license notices. Node.js is no longer embedded in the
main APK; the verified pack is required for Node.js projects.

Build and verify both APKs:

```sh
./gradlew :app:assembleDebug :runtime-node:assembleDebug
./scripts/verify-runtime-pack.sh
```

`runtime-php` is the second pack. It contains PHP CLI, the built-in web server,
and statically linked SQLite support. The binary and pack are reproducible with
`scripts/build-php-runtime.sh` and verified by
`scripts/verify-php-runtime-pack.sh`. The main app verifies the installed pack
before enabling PHP project setup, then owns project synchronization, the
foreground service, process logs, runtime metrics, and tunnel lifecycle.

`runtime-mariadb` is an optional database pack. It contains MariaDB Community
Server, its direct Android libraries, and the data required to create a private
database directory. The server is never started by the pack itself.

Install the runtime before the main app when testing a clean device:

```sh
adb install -r runtime-node/build/outputs/apk/debug/runtime-node-debug.apk
adb install -r runtime-php/build/outputs/apk/debug/runtime-php-debug.apk
adb install -r runtime-mariadb/build/outputs/apk/debug/runtime-mariadb-debug.apk
adb install -r runtime-connector/build/outputs/apk/debug/runtime-connector-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Adding a runtime

Each runtime needs a unique package ID, a reproducible native build, fixed
payload hashes, license notices, and physical-device tests. Add the pack to the
main manifest's package visibility list and register its trusted specification
in `RuntimePacks.kt`. Do not mark a project type available until its real runtime
and lifecycle implementation pass device tests.
