#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

sha256sum --check <<'CHECKSUMS'
cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c  runtime-payloads/node/jniLibs/arm64-v8a/libc++_shared.so
218a64c6c09cdb0cb9c751cde31b976c0a1c4db9fd6d6d512c4ef96f374840e1  app/src/main/jniLibs/arm64-v8a/libcloudflared.so
c03a6a63832dc27dec14d6879a194242c7f2142301471c18d67354fe137d2b6a  runtime-payloads/node/jniLibs/arm64-v8a/libnode_runtime.so
e8e8c951a85400bc5706564197dfbf4f1682b52dcc3cba1731041d6b5f49424e  runtime-payloads/node/assets/node/npm.zip
CHECKSUMS

echo "Bundled runtime checksums verified."
