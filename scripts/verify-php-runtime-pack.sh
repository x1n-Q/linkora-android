#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk="${1:-$repo_root/runtime-php/build/outputs/apk/debug/runtime-php-debug.apk}"
runtime="$repo_root/runtime-payloads/php/jniLibs/arm64-v8a/libphp_runtime.so"
runtime_sha256="6162e6cd7850c0eee23837859d2240a4649b398c8263cfbb19984a375e0b8576"

for command in unzip sha256sum file grep; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

[[ -f "$runtime" ]] || {
  echo "PHP runtime is missing: $runtime" >&2
  exit 1
}
file "$runtime" | grep -q "ARM aarch64" || {
  echo "PHP runtime is not an ARM64 ELF executable." >&2
  exit 1
}
for module in mysqli pdo_mysql mysqlnd; do
  grep -aFq "$module" "$runtime" || {
    echo "PHP runtime is missing $module support." >&2
    exit 1
  }
done
echo "$runtime_sha256  $runtime" | sha256sum --check --status || {
  echo "PHP runtime failed its integrity check." >&2
  exit 1
}
[[ -f "$apk" ]] || {
  echo "Runtime pack APK is missing: $apk" >&2
  exit 1
}

entries="$(unzip -Z1 "$apk")"
for required in \
  lib/arm64-v8a/libphp_runtime.so \
  assets/PHP-LICENSE.txt \
  assets/SQLite-NOTICE.txt \
  assets/Android-NDK-NOTICE.txt \
  AndroidManifest.xml; do
  grep -Fxq "$required" <<<"$entries" || {
    echo "PHP Runtime Pack APK is missing $required" >&2
    exit 1
  }
done

sha256sum "$runtime"
echo "PHP Runtime Pack APK verified: $(basename "$apk")"
