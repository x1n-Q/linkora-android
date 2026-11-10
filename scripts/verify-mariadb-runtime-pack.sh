#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk="${1:-$repo_root/runtime-mariadb/build/outputs/apk/debug/runtime-mariadb-debug.apk}"
payload="$repo_root/runtime-payloads/mariadb/jniLibs/arm64-v8a"

for command in file readelf sha256sum unzip; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

server="$payload/libmariadbd_runtime.so"
[[ -f "$server" ]] || {
  echo "MariaDB runtime is missing: $server" >&2
  exit 1
}
file "$server" | grep -q "ARM aarch64" || {
  echo "MariaDB runtime is not an ARM64 ELF executable." >&2
  exit 1
}

allowed_dependencies='^(libandroid-support\.so|libpcre2-8\.so|libcrypt\.so|liblinkora_z\.so|liblinkora_ssl\.so|liblinkora_crypto\.so|libc\+\+_shared\.so|libm\.so|libdl\.so|libc\.so)$'
while read -r dependency; do
  grep -Eq "$allowed_dependencies" <<<"$dependency" || {
    echo "MariaDB has an unpackaged dependency: $dependency" >&2
    exit 1
  }
done < <(readelf -d "$server" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p')

[[ -f "$apk" ]] || {
  echo "Runtime pack APK is missing: $apk" >&2
  exit 1
}

entries="$(unzip -Z1 "$apk")"
for required in \
  lib/arm64-v8a/libmariadbd_runtime.so \
  lib/arm64-v8a/libandroid-support.so \
  lib/arm64-v8a/libpcre2-8.so \
  lib/arm64-v8a/libcrypt.so \
  lib/arm64-v8a/liblinkora_z.so \
  lib/arm64-v8a/liblinkora_ssl.so \
  lib/arm64-v8a/liblinkora_crypto.so \
  lib/arm64-v8a/libc++_shared.so \
  assets/mariadb/share.zip \
  assets/licenses/MariaDB-GPL-2.0.txt \
  assets/licenses/OpenSSL-Apache-2.0.txt \
  assets/licenses/Android-NDK-libc++-NOTICE.txt \
  assets/licenses/SOURCE-OFFER.txt \
  AndroidManifest.xml; do
  grep -Fxq "$required" <<<"$entries" || {
    echo "MariaDB Runtime Pack APK is missing $required" >&2
    exit 1
  }
done

sha256sum "$payload"/*.so "$repo_root/runtime-payloads/mariadb/assets/mariadb/share.zip"
echo "MariaDB Runtime Pack APK verified: $(basename "$apk")"
