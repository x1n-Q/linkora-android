#!/usr/bin/env bash
set -euo pipefail

credentials_file="${LINKORA_SIGNING_ENV:-$HOME/.config/linkora-android/signing.env}"

if [[ -f "$credentials_file" ]]; then
  # The local credential file is outside the repository and must be mode 0600.
  # shellcheck disable=SC1090
  source "$credentials_file"
fi

required=(
  LINKORA_KEYSTORE_PATH
  LINKORA_STORE_PASSWORD
  LINKORA_KEY_ALIAS
  LINKORA_KEY_PASSWORD
)
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing $variable. Configure $credentials_file or export it." >&2
    exit 1
  fi
  export "$variable"
done

if [[ ! -f "$LINKORA_KEYSTORE_PATH" ]]; then
  echo "Signing keystore not found: $LINKORA_KEYSTORE_PATH" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

./gradlew \
  :app:assembleRelease \
  :runtime-node:assembleRelease \
  :runtime-php:assembleRelease \
  :runtime-mariadb:assembleRelease \
  :runtime-connector:assembleRelease \
  "$@"
