# Android connector build

Linkora bundles an Android ARM64 build of `cloudflared` as a native executable
library. Android installs this file as signed executable code; extracting an
executable into app-writable storage is blocked by SELinux.

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

The output belongs at:

```text
app/src/main/jniLibs/arm64-v8a/libcloudflared.so
```

Expected SHA-256:

```text
218a64c6c09cdb0cb9c751cde31b976c0a1c4db9fd6d6d512c4ef96f374840e1
```
