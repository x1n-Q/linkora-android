#!/usr/bin/env bash
set -euo pipefail

PHP_VERSION="8.4.27"
PHP_SOURCE_SHA256="dcf93923b0e38e0d5252b25b796520b5a47761a08b3488b6d0b5d1115764d3f0"
SQLITE_VERSION="3470200"
SQLITE_SOURCE_SHA256="f1b2ee412c28d7472bc95ba996368d6f0cdcf00362affdadb27ed286c179540b"
ANDROID_API="26"
ANDROID_ABI="arm64-v8a"
PHP_BUILD_JOBS="${PHP_BUILD_JOBS:-4}"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "Set ANDROID_NDK_HOME to Android NDK r28 or newer." >&2
  exit 1
fi

for command in curl sha256sum tar make patch install file; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
target_prefix="aarch64-linux-android"
cc="$toolchain/bin/${target_prefix}${ANDROID_API}-clang"
cxx="$toolchain/bin/${target_prefix}${ANDROID_API}-clang++"
ar="$toolchain/bin/llvm-ar"
ranlib="$toolchain/bin/llvm-ranlib"
strip="$toolchain/bin/llvm-strip"

for tool in "$cc" "$cxx" "$ar" "$ranlib" "$strip"; do
  [[ -x "$tool" ]] || {
    echo "NDK tool is missing: $tool" >&2
    exit 1
  }
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_work_dir=false
if [[ -n "${PHP_BUILD_WORK_DIR:-}" ]]; then
  work_dir="$PHP_BUILD_WORK_DIR"
  mkdir -p "$work_dir"
else
  work_dir="$(mktemp -d -t linkora-php-runtime.XXXXXX)"
  temporary_work_dir=true
fi

php_archive="$work_dir/php-${PHP_VERSION}.tar.xz"
php_source="$work_dir/php-${PHP_VERSION}"
sqlite_archive="$work_dir/sqlite-autoconf-${SQLITE_VERSION}.tar.gz"
sqlite_source="$work_dir/sqlite-autoconf-${SQLITE_VERSION}"
sqlite_prefix="$work_dir/sqlite-prefix"
output="$repo_root/runtime-payloads/php/jniLibs/$ANDROID_ABI/libphp_runtime.so"

cleanup() {
  if [[ "$temporary_work_dir" == true ]]; then
    rm -rf "$work_dir"
  fi
}
trap cleanup EXIT

download_verified() {
  local url="$1"
  local destination="$2"
  local checksum="$3"
  if [[ ! -f "$destination" ]]; then
    curl --fail --location --silent --show-error "$url" --output "$destination"
  fi
  echo "$checksum  $destination" | sha256sum --check --status
}

download_verified \
  "https://www.php.net/distributions/php-${PHP_VERSION}.tar.xz" \
  "$php_archive" \
  "$PHP_SOURCE_SHA256"
download_verified \
  "https://www.sqlite.org/2024/sqlite-autoconf-${SQLITE_VERSION}.tar.gz" \
  "$sqlite_archive" \
  "$SQLITE_SOURCE_SHA256"

if [[ ! -d "$sqlite_source" ]]; then
  tar -xzf "$sqlite_archive" -C "$work_dir"
fi
if [[ ! -f "$sqlite_prefix/lib/libsqlite3.a" ]]; then
  (
    cd "$sqlite_source"
    env \
      CC="$cc" \
      AR="$ar" \
      RANLIB="$ranlib" \
      CFLAGS="-O2 -fPIC -DSQLITE_ENABLE_FTS5 -DSQLITE_ENABLE_RTREE" \
      ac_cv_header_zlib_h=no \
      ./configure \
        --host="$target_prefix" \
        --build=x86_64-pc-linux-gnu \
        --prefix="$sqlite_prefix" \
        --disable-shared \
        --enable-static
    make -j"$PHP_BUILD_JOBS"
    make install
  )
fi

if [[ ! -d "$php_source" ]]; then
  tar -xJf "$php_archive" -C "$work_dir"
fi

cd "$php_source"
if [[ ! -f .linkora-android-patched ]]; then
  patch --strip=1 < "$repo_root/scripts/patches/php-${PHP_VERSION}-android.patch"
  touch .linkora-android-patched
fi

if [[ ! -f .linkora-android-configured ]]; then
  env \
    CC="$cc" \
    CXX="$cxx" \
    AR="$ar" \
    RANLIB="$ranlib" \
    STRIP="$strip" \
    SQLITE_CFLAGS="-I$sqlite_prefix/include" \
    SQLITE_LIBS="-L$sqlite_prefix/lib -lsqlite3 -lm" \
    CFLAGS="-O2 -fPIC" \
    LDFLAGS="-Wl,-z,max-page-size=16384" \
    ac_cv_c_bigendian_php=no \
    ac_cv_func_dn_skipname=no \
    ac_cv_func_res_nsearch=no \
    php_cv_sizeof_intmax_t=8 \
    ./configure \
      --host="$target_prefix" \
      --build=x86_64-pc-linux-gnu \
      --prefix=/data/user/0/top.linkora.mobile/files/php \
      --disable-all \
      --disable-cgi \
      --disable-phpdbg \
      --without-pear \
      --without-libxml \
      --without-openssl \
      --without-curl \
      --without-zlib \
      --enable-bcmath \
      --enable-calendar \
      --enable-ctype \
      --enable-exif \
      --enable-fileinfo \
      --enable-filter \
      --enable-mysqlnd \
      --disable-mysqlnd-compression-support \
      --enable-pdo \
      --enable-session \
      --enable-sockets \
      --enable-tokenizer \
      --with-mhash \
      --with-mysqli=mysqlnd \
      --with-pdo-mysql=mysqlnd \
      --with-sqlite3 \
      --with-pdo-sqlite
  patch --strip=1 < "$repo_root/scripts/patches/php-${PHP_VERSION}-android-config.patch"
  touch .linkora-android-configured
fi

make -j"$PHP_BUILD_JOBS"
"$strip" --strip-unneeded sapi/cli/php
install -Dm755 sapi/cli/php "$output"
install -Dm644 LICENSE "$repo_root/app/src/main/assets/licenses/PHP-LICENSE.txt"
install -Dm644 "$ANDROID_NDK_HOME/NOTICE.toolchain" \
  "$repo_root/app/src/main/assets/licenses/Android-NDK-NOTICE.txt"

sha256sum "$output"
file "$output"
