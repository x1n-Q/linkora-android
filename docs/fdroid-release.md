# F-Droid release preparation

The repository contains upstream store metadata and an active `.fdroid.yml`
recipe for the architecture-neutral main application. The recipe deliberately
removes prebuilt runtime payloads before F-Droid scans and builds the source.

## Before submission

1. Make the source repository publicly cloneable.
2. Confirm the application version and version code in `app/build.gradle.kts`.
3. Confirm the build entry and changelog use the same version and version code.
4. Run `./gradlew clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`
   with JDK 17.
5. Test the main release build on at least ARM64 and x86_64 Android devices.
6. Simulate the F-Droid checkout preparation and build using the commands in
   the next section.
7. Commit all release sources and create the matching signed source tag, such
   as `v0.7.0`.
8. Push the commit and tag, then confirm both are publicly cloneable without
   GitHub credentials.
9. Add the final tag and commit to the fdroiddata submission. Do not submit a
   branch name as the release commit.

## Reproduce the F-Droid main-app build

Run these commands in a disposable checkout, never in the working tree:

```bash
rm -rf app/src/main/jniLibs runtime-payloads
sed -i '/include(":runtime-/d' settings.gradle.kts
./gradlew clean assembleRelease
```

Only `app/build/outputs/apk/release/app-release-unsigned.apk` should be
produced. The APK must not contain Node.js, PHP, MariaDB, or cloudflared.

## Native runtime review

F-Droid scans executable files in the source tree. Linkora keeps ARM64 builds
of Node.js, PHP, MariaDB, cloudflared, and the Android NDK C++ runtime for its
separate runtime-pack applications. The main app does not package those native
runtimes. Source versions, build instructions, licenses, and SHA-256 values are
recorded in:

- `docs/node-runtime-build.md`
- `docs/connector-build.md`
- `THIRD_PARTY_NOTICES.md`
- `scripts/verify-bundled-runtime.sh`
- `docs/runtime-packs.md`

Each runtime pack has its own application ID and requires a separate F-Droid
metadata submission with a source-build recipe. Linkora can also install a
compatible pack from a signed GitHub release after explicit user approval.

The main-app recipe uses `rm` rather than `scanignore`: every prebuilt native
payload is deleted before scanning, and Gradle builds only the main app. Never
add a runtime payload to the main recipe's `scanignore` list.

## Signing

The Gradle release task intentionally produces an unsigned APK. F-Droid signs
source-built APKs with its repository key unless a reproducible-build workflow
using the developer's signing key is configured. Never commit a keystore,
password, or signing property file.
