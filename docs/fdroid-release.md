# F-Droid release preparation

The repository contains upstream store metadata and a disabled `.fdroid.yml`
recipe. It is prepared for review but must not be submitted until the release
is final.

## Before submission

1. Make the source repository publicly cloneable.
2. Confirm the application version and version code in `app/build.gradle.kts`.
3. Update the build entry in `.fdroid.yml` to the same version.
4. Run `./scripts/verify-bundled-runtime.sh`.
5. Run `./gradlew clean :app:assembleRelease` with JDK 17.
6. Test the release build on a physical ARM64 Android device.
7. Commit all release sources and create the matching signed source tag, such
   as `v0.5.0`.
8. Remove the build entry's `disable` field only after that tag is public.
9. Add the final tag and commit to the fdroiddata submission. Do not submit a
   branch name as the release commit.

## Native runtime review

F-Droid scans bundled executable files. Linkora includes ARM64 builds of
Node.js, cloudflared, and the Android NDK C++ runtime plus a deterministic npm
archive. Their source versions, build instructions, licenses, and SHA-256
values are recorded in:

- `docs/node-runtime-build.md`
- `docs/connector-build.md`
- `THIRD_PARTY_NOTICES.md`
- `scripts/verify-bundled-runtime.sh`

The local metadata uses narrowly scoped `scanignore` entries for these known
files. F-Droid maintainers may request that native artifacts be rebuilt in the
fdroiddata recipe. Do not broaden those entries or ignore unrelated findings.

## Signing

The Gradle release task intentionally produces an unsigned APK. F-Droid signs
source-built APKs with its repository key unless a reproducible-build workflow
using the developer's signing key is configured. Never commit a keystore,
password, or signing property file.
