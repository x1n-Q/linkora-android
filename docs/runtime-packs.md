# Runtime packs

Linkora can load large native runtimes from separately installed, headless
Android packages. A runtime pack has no launcher activity, service, receiver,
or background process. Android Package Manager installs its native payload as
read-only executable code, while the main Linkora app owns project files,
foreground services, logs, and process lifecycle.

## Package contract

The first contract version requires these application metadata values:

| Key | Node.js value | PHP value |
| --- | --- | --- |
| `top.linkora.runtime.ID` | `node` | `php` |
| `top.linkora.runtime.PROTOCOL` | `1` | `1` |
| `top.linkora.runtime.VERSION` | `24.22.1` | `8.4.27` |
| `top.linkora.runtime.ABI` | `arm64-v8a` | `arm64-v8a` |
| `top.linkora.runtime.EXECUTABLE` | `libnode_runtime.so` | `libphp_runtime.so` |

The main app declares the known package IDs in its manifest `<queries>` block.
It reads `ApplicationInfo.nativeLibraryDir`, validates the contract metadata,
checks every native payload and asset against hashes pinned in the main app,
and confirms that the executable has Package Manager-provided execute access.
Package names and metadata alone are never treated as proof of integrity.

The pack's signing certificate is also compared with the main app certificate.
A matching certificate is recorded as stronger provenance, while pinned payload
hashes keep independently signed store builds verifiable.

## Node.js migration

`runtime-node` is the reference pack. It contains Node.js, the Android NDK C++
runtime, npm, and their license notices. During the migration release, the same
payload remains in the main APK as a fallback. Linkora prefers the verified
installed pack and falls back only when no valid pack is available. A later
release can remove the built-in copy after runtime-pack installation and update
flows are established.

Build and verify both APKs:

```sh
./gradlew :app:assembleDebug :runtime-node:assembleDebug
./scripts/verify-runtime-pack.sh
```

`runtime-php` is the second pack. It contains PHP CLI, the built-in web server,
and statically linked SQLite support. The binary and pack are reproducible with
`scripts/build-php-runtime.sh` and verified by
`scripts/verify-php-runtime-pack.sh`. PHP remains unavailable in project setup
until its foreground-service lifecycle is integrated in the main app.

Install the runtime before the main app when testing a clean device:

```sh
adb install -r runtime-node/build/outputs/apk/debug/runtime-node-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Adding a runtime

Each runtime needs a unique package ID, a reproducible native build, fixed
payload hashes, license notices, and physical-device tests. Add the pack to the
main manifest's package visibility list and register its trusted specification
in `RuntimePacks.kt`. Do not mark a project type available until its real runtime
and lifecycle implementation pass device tests.
