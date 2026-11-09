# Android PHP runtime build

Linkora packages PHP as Android-signed ARM64 executable code in the separate,
headless `top.linkora.runtime.php` application. The main app owns project files,
the foreground service, logs, and process lifecycle.

## Inputs

- PHP `8.4.27`
- SQLite `3.47.2`
- Android NDK `28.2.13676358`
- minimum Android API `26`
- target ABI `arm64-v8a`

The first pack includes PHP CLI, the built-in development server, PDO SQLite,
SQLite3, sessions, sockets, fileinfo, tokenizer, filter, ctype, bcmath,
calendar, and EXIF. OpenSSL, cURL, XML, and MySQL clients are not included in
this first build and must not be presented as supported.

## Build

```sh
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
./scripts/build-php-runtime.sh
./gradlew :runtime-php:assembleDebug
./scripts/verify-php-runtime-pack.sh
```

Set `PHP_BUILD_WORK_DIR` to preserve downloaded sources and build objects:

```sh
PHP_BUILD_WORK_DIR="$HOME/.cache/linkora-php-8.4.27" \
  ./scripts/build-php-runtime.sh
```

The Android patch uses `sysconf(_SC_OPEN_MAX)` instead of Bionic's missing
`getdtablesize()` and only registers full DNS record functions when Bionic
provides the required parser functions. It does not alter PHP language
behavior.

The resulting executable is stripped, linked only to Android system libraries,
and aligned for Android devices using 16 KB memory pages. Test the generated
pack on a physical ARM64 device before changing the trusted hash in the main
application.
