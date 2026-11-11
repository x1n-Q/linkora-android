#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk="${1:-$repo_root/runtime-connector/build/outputs/apk/debug/runtime-connector-debug.apk}"
runtime="$repo_root/app/src/main/jniLibs/arm64-v8a/libcloudflared.so"
runtime_sha256="218a64c6c09cdb0cb9c751cde31b976c0a1c4db9fd6d6d512c4ef96f374840e1"

for command in cut file sha256sum unzip; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

[[ -f "$runtime" ]] || {
  echo "Connector runtime is missing: $runtime" >&2
  exit 1
}
file "$runtime" | grep -q "ARM aarch64" || {
  echo "Connector runtime is not an ARM64 ELF executable." >&2
  exit 1
}
echo "$runtime_sha256  $runtime" | sha256sum --check --status || {
  echo "Connector runtime failed its integrity check." >&2
  exit 1
}
[[ -f "$apk" ]] || {
  echo "Connector pack APK is missing: $apk" >&2
  exit 1
}

entry="lib/arm64-v8a/libcloudflared.so"
unzip -Z1 "$apk" | grep -Fxq "$entry" || {
  echo "Connector pack APK is missing $entry" >&2
  exit 1
}
apk_hash="$(unzip -p "$apk" "$entry" | sha256sum | cut -d ' ' -f 1)"
[[ "$apk_hash" == "$runtime_sha256" ]] || {
  echo "Connector pack APK altered $entry during packaging." >&2
  exit 1
}

echo "Linkora Connector Pack APK verified: $(basename "$apk")"
