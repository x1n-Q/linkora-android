#!/usr/bin/env bash
set -euo pipefail

RELEASE_CERT_SHA256="30938a07ba42ca042033a78ac75f1730a5463d780e3d9a9119a762fa540c222d"
MARIADB_SOURCE_SHA256="b2fb30a2ba92d2c5dc08b5ce3353575f43c988cc6362fce44a447b805e9be1a2"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_tools="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools"
apksigner="$(find "$build_tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort -V | tail -1)"
apkanalyzer="$(find "${ANDROID_HOME:-$HOME/Android/Sdk}" -type f -name apkanalyzer | sort -V | tail -1)"

[[ -x "$apksigner" ]] || { echo "Android apksigner was not found." >&2; exit 1; }
[[ -x "$apkanalyzer" ]] || { echo "Android apkanalyzer was not found." >&2; exit 1; }

declare -A hashes=(
  [node]="059abe5b775eac0ad71d15cbd3861c8bff79532f85a2e64be54a7455881f5321"
  [php]="16954b75e4e5bdbf59cf278f52f15b3c05caddeb4fb3249509ca8574aa13405e"
  [mariadb]="362cc87faadf09d88409c02af084ca258688ede75a7236e1b0e688bb61d33071"
  [connector]="80359dc282fefb43d0af7939663a15e5dfe06f42a6a455c3b818d5df09d3ee4a"
)

verify_apk() {
  local module="$1"
  local apk="$repo_root/runtime-$module/build/outputs/apk/release/runtime-$module-release.apk"
  [[ -f "$apk" ]] || { echo "Missing release APK: $apk" >&2; exit 1; }

  echo "${hashes[$module]}  $apk" | sha256sum --check --status || {
    echo "$module release APK does not match the pinned catalog hash." >&2
    exit 1
  }
  "$apksigner" verify --verbose "$apk" >/dev/null

  local certificate
  certificate="$("$apksigner" verify --print-certs "$apk" 2>/dev/null |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
  [[ "$certificate" == "$RELEASE_CERT_SHA256" ]] || {
    echo "$module release APK has the wrong signer." >&2
    exit 1
  }

  local manifest
  manifest="$("$apkanalyzer" manifest print "$apk")"
  grep -Fq 'android:hasCode="false"' <<<"$manifest" || {
    echo "$module release APK is not marked as a code-free container." >&2
    exit 1
  }
  if grep -Eq '<(activity|activity-alias|service|receiver|provider)([ >])' <<<"$manifest"; then
    echo "$module release APK declares an Android component." >&2
    exit 1
  fi

  local verifier="$repo_root/scripts/verify-$module-runtime-pack.sh"
  if [[ "$module" == "node" ]]; then
    verifier="$repo_root/scripts/verify-runtime-pack.sh"
  fi
  "$verifier" "$apk" >/dev/null
  echo "$module: signed, pinned, headless, and payload-verified"
}

verify_apk node
verify_apk php
verify_apk mariadb
verify_apk connector

app="$repo_root/app/build/outputs/apk/release/app-release.apk"
[[ -f "$app" ]] || { echo "Missing signed app release APK." >&2; exit 1; }
"$apksigner" verify --verbose "$app" >/dev/null
app_certificate="$("$apksigner" verify --print-certs "$app" 2>/dev/null |
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ "$app_certificate" == "$RELEASE_CERT_SHA256" ]] || {
  echo "The Linkora app release has the wrong signer." >&2
  exit 1
}
echo "app: production-signed and signature-verified"

source_archive="$repo_root/dist/linkora-runtime-mariadb-13.0.2-source.tar.gz"
echo "$MARIADB_SOURCE_SHA256  $source_archive" | sha256sum --check --status || {
  echo "The MariaDB corresponding-source archive is missing or changed." >&2
  exit 1
}
echo "mariadb source: exact archive verified"
