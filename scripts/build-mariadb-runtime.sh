#!/usr/bin/env bash
set -euo pipefail

MARIADB_VERSION="13.0.2"
ANDROID_ABI="arm64-v8a"
REPOSITORY="https://packages.termux.dev/apt/termux-main"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_work_dir=false
if [[ -n "${MARIADB_BUILD_WORK_DIR:-}" ]]; then
  work_dir="$MARIADB_BUILD_WORK_DIR"
  mkdir -p "$work_dir"
else
  work_dir="$(mktemp -d -t linkora-mariadb-runtime.XXXXXX)"
  temporary_work_dir=true
fi

cleanup() {
  if [[ "$temporary_work_dir" == true ]]; then
    rm -rf "$work_dir"
  fi
}
trap cleanup EXIT

for command in curl dpkg-deb install patchelf readelf sha256sum zip; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

packages=(
  "pool/main/m/mariadb/mariadb_2:13.0.2_aarch64.deb|df1249a2de5faf1878a17b4b7188362891cb81ce0267d6c3150891a013975062"
  "pool/main/liba/libandroid-support/libandroid-support_29-1_aarch64.deb|f2f145d6135ad4843ac9670153be3e3944dc1e6f1736d46d2306c28f2b86f517"
  "pool/main/libc/libc++/libc++_30_aarch64.deb|53d0b84a7ba7459024257cb94d5b136fe13ef858567f65a8064b35950799f2ca"
  "pool/main/libc/libcrypt/libcrypt_0.2-6_aarch64.deb|6c283eed576b98cc3568f99638156f8588f77d979579d03bef8683d6eb8601e1"
  "pool/main/o/openssl/openssl_1:3.6.5_aarch64.deb|a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b"
  "pool/main/p/pcre2/pcre2_10.49_aarch64.deb|c27995e5f52b8ecc9b8b3e662eb08e49f82a18bfd3f34343936acc7b23964724"
  "pool/main/z/zlib/zlib_1.4_aarch64.deb|e3027565ae50bcd8ba5113c988b589754ae192c9cc3e6afbe3e4ed7c06b2598e"
)

downloads="$work_dir/downloads"
extracted="$work_dir/extracted"
stage="$work_dir/stage"
mkdir -p "$downloads" "$extracted" "$stage/share"

for package in "${packages[@]}"; do
  relative_path="${package%%|*}"
  expected_hash="${package##*|}"
  archive="$downloads/$(basename "$relative_path")"
  if [[ ! -f "$archive" ]]; then
    curl --fail --location --silent --show-error \
      "$REPOSITORY/$relative_path" \
      --output "$archive"
  fi
  echo "$expected_hash  $archive" | sha256sum --check --status
  dpkg-deb --extract "$archive" "$extracted"
done

prefix="$extracted/data/data/com.termux/files/usr"
native_output="$repo_root/runtime-payloads/mariadb/jniLibs/$ANDROID_ABI"
asset_output="$repo_root/runtime-payloads/mariadb/assets"
license_output="$asset_output/licenses"
mkdir -p "$native_output" "$asset_output/mariadb" "$license_output"

install -m755 "$prefix/bin/mariadbd" "$native_output/libmariadbd_runtime.so"
install -m755 "$prefix/lib/libandroid-support.so" "$native_output/libandroid-support.so"
install -m755 "$prefix/lib/libpcre2-8.so" "$native_output/libpcre2-8.so"
install -m755 "$prefix/lib/libcrypt.so" "$native_output/libcrypt.so"
install -m755 "$prefix/lib/libz.so.1" "$native_output/liblinkora_z.so"
install -m755 "$prefix/lib/libssl.so.3" "$native_output/liblinkora_ssl.so"
install -m755 "$prefix/lib/libcrypto.so.3" "$native_output/liblinkora_crypto.so"
install -m755 "$prefix/lib/libc++_shared.so" "$native_output/libc++_shared.so"

server="$native_output/libmariadbd_runtime.so"
patchelf --set-rpath '$ORIGIN' "$server"
patchelf --replace-needed libz.so.1 liblinkora_z.so "$server"
patchelf --replace-needed libssl.so.3 liblinkora_ssl.so "$server"
patchelf --replace-needed libcrypto.so.3 liblinkora_crypto.so "$server"

patchelf --set-soname liblinkora_z.so "$native_output/liblinkora_z.so"
patchelf --set-soname liblinkora_ssl.so "$native_output/liblinkora_ssl.so"
patchelf --set-soname liblinkora_crypto.so "$native_output/liblinkora_crypto.so"
patchelf --replace-needed libcrypto.so.3 liblinkora_crypto.so \
  "$native_output/liblinkora_ssl.so"
patchelf --replace-needed libcrypto.so.3 liblinkora_crypto.so \
  "$native_output/libcrypt.so"

for library in "$native_output"/*.so; do
  patchelf --set-rpath '$ORIGIN' "$library"
done

share_files=(
  charsets
  english/errmsg.sys
  fill_help_tables.sql
  maria_add_gis_sp_bootstrap.sql
  mariadb_performance_tables.sql
  mariadb_sys_schema.sql
  mariadb_system_tables.sql
  mariadb_system_tables_data.sql
)
for source in "${share_files[@]}"; do
  if [[ -d "$prefix/share/mariadb/$source" ]]; then
    mkdir -p "$stage/share/$source"
    cp -R "$prefix/share/mariadb/$source/." "$stage/share/$source/"
  else
    install -Dm644 "$prefix/share/mariadb/$source" "$stage/share/$source"
  fi
done

find "$stage/share" -exec touch -t 202601010000 {} +
(
  cd "$stage/share"
  zip -X -q -r "$asset_output/mariadb/share.zip" .
)

install -Dm644 "$prefix/share/doc/COPYING" "$license_output/MariaDB-GPL-2.0.txt"
install -Dm644 "$prefix/share/doc/THIRDPARTY" "$license_output/MariaDB-THIRDPARTY.txt"
install -Dm644 "$prefix/share/doc/libandroid-support/LICENSE.txt" \
  "$license_output/libandroid-support-LICENSE.txt"
install -Dm644 "$prefix/share/doc/libcrypt/copyright" \
  "$license_output/libcrypt-copyright.txt"
install -Dm644 "$prefix/share/doc/pcre2/LICENCE.md" \
  "$license_output/PCRE2-LICENSE.md"
install -Dm644 "$prefix/share/doc/zlib/copyright" \
  "$license_output/zlib-copyright.txt"
install -Dm644 "$repo_root/app/src/main/assets/licenses/Apache-2.0.txt" \
  "$license_output/OpenSSL-Apache-2.0.txt"
install -Dm644 "$repo_root/app/src/main/assets/licenses/Android-NDK-NOTICE.txt" \
  "$license_output/Android-NDK-libc++-NOTICE.txt"

cat > "$license_output/SOURCE-OFFER.txt" <<EOF
Linkora MariaDB Runtime Pack ${MARIADB_VERSION}

Complete corresponding source and the exact build materials are distributed
beside the runtime APK:
https://github.com/x1n-Q/linkora-runtime-packs/releases/download/runtime-packs-v0.7.0/linkora-runtime-mariadb-${MARIADB_VERSION}-source.tar.gz

MariaDB source archive SHA-256:
8ee8d955530faedcea344535de85495afb5b98128bbf660e56893e93e21ee8f7

The bundle includes MariaDB 13.0.2 source, the complete Termux build tree at
commit 22880cd43c4bb44e8a9eabcec9a075e8c8bf473b, all MariaDB Android patches,
Linkora's deterministic repackaging script, binary-input checksums, licenses,
and reproduction instructions. No moving branch is used.
EOF

printf 'Native payload hashes:\n'
sha256sum "$native_output"/*.so
printf 'Asset hash:\n'
sha256sum "$asset_output/mariadb/share.zip"
printf 'Server dependencies:\n'
readelf -d "$server" | grep -E 'NEEDED|RUNPATH'
