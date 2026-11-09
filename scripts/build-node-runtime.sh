#!/usr/bin/env bash
set -euo pipefail

NODE_VERSION="24.22.1"
NODE_SOURCE_SHA256="4f5379796280cdaeb0c2f358fd04b6a16cc4fb7dfa83b5a8c91a9d963b435231"
ANDROID_API="26"
ANDROID_ABI="arm64-v8a"
NODE_BUILD_JOBS="${NODE_BUILD_JOBS:-4}"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "Set ANDROID_NDK_HOME to Android NDK r28 or newer." >&2
  exit 1
fi

for command in curl sha256sum tar make patch gcc g++ install file; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
target_prefix="aarch64-linux-android"
cc="$toolchain/bin/${target_prefix}${ANDROID_API}-clang"
cxx="$toolchain/bin/${target_prefix}${ANDROID_API}-clang++"
strip="$toolchain/bin/llvm-strip"

for tool in "$cc" "$cxx" "$strip"; do
  [[ -x "$tool" ]] || {
    echo "NDK tool is missing: $tool" >&2
    exit 1
  }
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_work_dir=false
if [[ -n "${NODE_BUILD_WORK_DIR:-}" ]]; then
  work_dir="$NODE_BUILD_WORK_DIR"
  mkdir -p "$work_dir"
else
  work_dir="$(mktemp -d -t linkora-node-runtime.XXXXXX)"
  temporary_work_dir=true
fi
archive="$work_dir/node-v${NODE_VERSION}.tar.xz"
source_dir="$work_dir/node-v${NODE_VERSION}"
output="$repo_root/runtime-payloads/node/jniLibs/$ANDROID_ABI/libnode_runtime.so"
cxx_runtime_output="$repo_root/runtime-payloads/node/jniLibs/$ANDROID_ABI/libc++_shared.so"

cleanup() {
  if [[ "$temporary_work_dir" == true ]]; then
    rm -rf "$work_dir"
  fi
}
trap cleanup EXIT

if [[ ! -d "$source_dir" ]]; then
  curl --fail --location --silent --show-error \
    "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}.tar.xz" \
    --output "$archive"
  echo "$NODE_SOURCE_SHA256  $archive" | sha256sum --check --status
  tar -xJf "$archive" -C "$work_dir"
fi

export CC="$cc"
export CXX="$cxx"
export CC_host="gcc"
export CXX_host="g++"
export GYP_DEFINES="target_arch=arm64 v8_target_arch=arm64 android_target_arch=arm64 host_os=linux OS=android android_ndk_path=$ANDROID_NDK_HOME"

cd "$source_dir"
if [[ ! -f .linkora-android-configured ]]; then
  patch --strip=1 < "$repo_root/scripts/patches/node-24.22.1-android-trap-handler.patch"
  patch --strip=1 < "$repo_root/scripts/patches/node-24.22.1-android-zlib.patch"
  ./configure \
    --dest-cpu=arm64 \
    --dest-os=android \
    --cross-compiling \
    --openssl-no-asm \
    --without-intl
  touch .linkora-android-configured
fi
make -j"$NODE_BUILD_JOBS"
"$strip" --strip-unneeded out/Release/node
install -Dm755 out/Release/node "$output"
install -Dm755 \
  "$toolchain/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" \
  "$cxx_runtime_output"
"$strip" --strip-unneeded "$cxx_runtime_output"
install -Dm644 "$ANDROID_NDK_HOME/NOTICE.toolchain" \
  "$repo_root/app/src/main/assets/licenses/Android-NDK-NOTICE.txt"

sha256sum "$output"
sha256sum "$cxx_runtime_output"
file "$output"
