# Android connector build

Linkora packages the Android ARM64 `cloudflared` build in the separate,
headless Linkora Connector Pack. Android installs it as signed executable code;
extracting an executable into app-writable storage is blocked by SELinux.

## Inputs

- cloudflared: `2026.10.4`
- source commit: `dc3b264da64a801bc3a8d5301a6db21401910ad5`
- Go: `1.27.3`
- Android NDK: `28.2.13676358`
- minimum Android API: `26`

## Build

From the matching cloudflared source checkout:

```sh
CC="$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"

CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$CC" go build \
  -trimpath \
  -ldflags='-s -w -X main.Version=2026.10.4 -X main.BuildTime=2026-10-26-0934-UTC' \
  -o libcloudflared.so \
  ./cmd/cloudflared
```

CGO must remain enabled so hostname lookups use Android's native resolver. The
pure-Go resolver falls back to loopback DNS on Android and cannot create a
tunnel reliably.

The source payload belongs at:

```text
app/src/main/jniLibs/arm64-v8a/libcloudflared.so
```

The main application excludes this directory from its APK. The
`runtime-connector` module packages the payload and the Runtime Manager installs
the compatible connector pack only when public URL support is needed.

Expected SHA-256:

```text
218a64c6c09cdb0cb9c751cde31b976c0a1c4db9fd6d6d512c4ef96f374840e1
```

Build and verify the pack with:

```sh
./gradlew :runtime-connector:assembleDebug
./scripts/verify-connector-runtime-pack.sh
```
