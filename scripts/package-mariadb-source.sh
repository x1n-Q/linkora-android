#!/usr/bin/env bash
set -euo pipefail

MARIADB_VERSION="13.0.2"
MARIADB_SOURCE_SHA256="8ee8d955530faedcea344535de85495afb5b98128bbf660e56893e93e21ee8f7"
TERMUX_COMMIT="22880cd43c4bb44e8a9eabcec9a075e8c8bf473b"
TERMUX_SOURCE_SHA256="59da4bd26de3f795907a55a6878449890c1269c7e57474cae95ca2cfa40e83fe"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cache_dir="${LINKORA_SOURCE_CACHE:-$HOME/.cache/linkora-android/source}"
output_dir="${LINKORA_SOURCE_OUTPUT:-$repo_root/dist}"
work_dir="$(mktemp -d -t linkora-mariadb-source.XXXXXX)"
trap 'rm -rf "$work_dir"' EXIT

mkdir -p "$cache_dir" "$output_dir"

download_verified() {
  local url="$1"
  local destination="$2"
  local expected_hash="$3"
  if [[ ! -f "$destination" ]]; then
    curl --fail --location --silent --show-error "$url" --output "$destination"
  fi
  echo "$expected_hash  $destination" | sha256sum --check --status || {
    rm -f "$destination"
    echo "Source checksum failed: $(basename "$destination")" >&2
    exit 1
  }
}

mariadb_archive="$cache_dir/mariadb-${MARIADB_VERSION}.tar.gz"
termux_archive="$cache_dir/termux-packages-${TERMUX_COMMIT}.tar.gz"

download_verified \
  "https://archive.mariadb.org/mariadb-${MARIADB_VERSION}/source/mariadb-${MARIADB_VERSION}.tar.gz" \
  "$mariadb_archive" \
  "$MARIADB_SOURCE_SHA256"
download_verified \
  "https://github.com/termux/termux-packages/archive/${TERMUX_COMMIT}.tar.gz" \
  "$termux_archive" \
  "$TERMUX_SOURCE_SHA256"

bundle="$work_dir/linkora-runtime-mariadb-${MARIADB_VERSION}-source"
mkdir -p "$bundle/upstream" "$bundle/build" "$bundle/notices"

install -m644 "$mariadb_archive" "$bundle/upstream/"
install -m644 "$termux_archive" "$bundle/upstream/"
install -m755 "$repo_root/scripts/build-mariadb-runtime.sh" "$bundle/build/"
install -m644 "$repo_root/docs/mariadb-runtime-build.md" "$bundle/build/"
install -m644 "$repo_root/runtime-payloads/mariadb/assets/licenses/SOURCE-OFFER.txt" \
  "$bundle/notices/"
install -m644 "$repo_root/runtime-payloads/mariadb/assets/licenses/MariaDB-GPL-2.0.txt" \
  "$bundle/notices/"
install -m644 "$repo_root/runtime-payloads/mariadb/assets/licenses/MariaDB-THIRDPARTY.txt" \
  "$bundle/notices/"

cat > "$bundle/README.md" <<EOF
# Linkora MariaDB Runtime Pack ${MARIADB_VERSION} source

This archive is the corresponding-source distribution for Linkora's MariaDB
Runtime Pack. It contains the unmodified MariaDB source archive, the complete
Termux build repository at the exact revision used by the Android recipe, and
Linkora's deterministic APK payload repackaging instructions.

## Verified inputs

- MariaDB: \`mariadb-${MARIADB_VERSION}.tar.gz\`
- MariaDB SHA-256: \`${MARIADB_SOURCE_SHA256}\`
- Termux build tree: \`termux-packages-${TERMUX_COMMIT}.tar.gz\`
- Termux commit: \`${TERMUX_COMMIT}\`
- Termux archive SHA-256: \`${TERMUX_SOURCE_SHA256}\`

The MariaDB recipe and all of its Android patches are under
\`packages/mariadb/\` in the Termux archive. Build the Termux package using the
included repository's documented Docker build environment, then run
\`build/build-mariadb-runtime.sh\` from a Linkora source checkout to verify and
repackage the pinned Android packages into the runtime payload.

The binary package URLs and SHA-256 values used by Linkora are embedded in that
script. Linkora changes only ELF dependency names and RUNPATH during packaging;
the MariaDB program logic is not modified.
EOF

cat > "$bundle/BINARY-INPUTS.sha256" <<'EOF'
df1249a2de5faf1878a17b4b7188362891cb81ce0267d6c3150891a013975062  mariadb_2:13.0.2_aarch64.deb
f2f145d6135ad4843ac9670153be3e3944dc1e6f1736d46d2306c28f2b86f517  libandroid-support_29-1_aarch64.deb
53d0b84a7ba7459024257cb94d5b136fe13ef858567f65a8064b35950799f2ca  libc++_30_aarch64.deb
6c283eed576b98cc3568f99638156f8588f77d979579d03bef8683d6eb8601e1  libcrypt_0.2-6_aarch64.deb
a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b  openssl_1:3.6.5_aarch64.deb
c27995e5f52b8ecc9b8b3e662eb08e49f82a18bfd3f34343936acc7b23964724  pcre2_10.49_aarch64.deb
e3027565ae50bcd8ba5113c988b589754ae192c9cc3e6afbe3e4ed7c06b2598e  zlib_1.4_aarch64.deb
EOF

(
  cd "$bundle"
  sha256sum upstream/* > SHA256SUMS
)

find "$bundle" -exec touch -d '2026-09-18T00:00:00Z' {} +
output="$output_dir/linkora-runtime-mariadb-${MARIADB_VERSION}-source.tar.gz"
tar --sort=name \
  --mtime='2026-09-18T00:00:00Z' \
  --owner=0 --group=0 --numeric-owner \
  --create --gzip --file "$output" \
  --directory "$work_dir" "$(basename "$bundle")"

sha256sum "$output"
