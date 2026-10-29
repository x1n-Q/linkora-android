#!/usr/bin/env bash
set -euo pipefail

NPM_VERSION="11.19.1"
NPM_TARBALL_SHA512="cedb312b1b7f92421a02cfb68b4194e88f8346651dacd6acc5364a25ef5309d4a32b19616a1ff8aff865e7280c0fa5c0835a99f5cd93368991e2a80ec9da75d2"

for command in curl sha512sum tar touch zip find sort install; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d -t linkora-npm-bundle.XXXXXX)"
archive="$work_dir/npm-${NPM_VERSION}.tgz"
source_dir="$work_dir/package"
output="$repo_root/app/src/main/assets/node/npm.zip"

cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT

curl --fail --location --silent --show-error \
  "https://registry.npmjs.org/npm/-/npm-${NPM_VERSION}.tgz" \
  --output "$archive"
echo "$NPM_TARBALL_SHA512  $archive" | sha512sum --check --status
tar -xzf "$archive" -C "$work_dir"

# npm's published tarball uses fixed file times. Normalize directories too so
# the zip is byte-for-byte reproducible regardless of when it is generated.
find "$source_dir" -exec touch -h -d '@315532800' {} +
mkdir -p "$(dirname "$output")"
rm -f "$output"
(
  cd "$source_dir"
  LC_ALL=C find . -mindepth 1 -print | LC_ALL=C sort | zip -X -q "$output" -@
)

install -Dm644 "$source_dir/LICENSE" \
  "$repo_root/app/src/main/assets/licenses/npm-LICENSE.txt"
sha256sum "$output"
