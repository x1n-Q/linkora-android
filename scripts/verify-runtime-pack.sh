#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk="${1:-$repo_root/runtime-node/build/outputs/apk/debug/runtime-node-debug.apk}"

for command in unzip sha256sum; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

"$repo_root/scripts/verify-bundled-runtime.sh"
[[ -f "$apk" ]] || {
  echo "Runtime pack APK is missing: $apk" >&2
  exit 1
}

entries="$(unzip -Z1 "$apk")"
for required in \
  lib/arm64-v8a/libnode_runtime.so \
  lib/arm64-v8a/libc++_shared.so \
  assets/node/npm.zip \
  assets/Node.js-LICENSE.txt \
  assets/npm-LICENSE.txt \
  assets/Android-NDK-NOTICE.txt \
  AndroidManifest.xml; do
  grep -Fxq "$required" <<<"$entries" || {
    echo "Runtime pack APK is missing $required" >&2
    exit 1
  }
done

echo "Node.js Runtime Pack APK verified: $(basename "$apk")"
