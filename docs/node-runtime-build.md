# Android Node.js runtime build

Linkora bundles Node.js as Android-signed ARM64 executable code. Android installs
the runtime from `jniLibs`; the app never downloads or executes native code from
writable storage.

## Inputs

- Node.js: `24.22.1` LTS
- source archive SHA-256:
  `4f5379796280cdaeb0c2f358fd04b6a16cc4fb7dfa83b5a8c91a9d963b435231`
- Android NDK: `28.2.13676358`
- minimum Android API: `26`
- target ABI: `arm64-v8a`

Internationalization data is omitted from the first Android runtime to reduce
the installed size. Core JavaScript, networking, TLS, filesystem, and package
execution remain available.

## Build

On Linux with GCC, `curl`, and the Android SDK installed:

```sh
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
./scripts/build-node-runtime.sh
```

For a resumable local build, set `NODE_BUILD_WORK_DIR` to a persistent cache
directory. Running the same command again will continue an interrupted `make`:

```sh
NODE_BUILD_WORK_DIR="$HOME/.cache/linkora-node-24.22.1" \
  ./scripts/build-node-runtime.sh
```

Node's Android helper sets the target compiler globally. Linkora's script keeps
`CC_host=gcc` and `CXX_host=g++` so V8 generators run on the Linux build host,
while Android code is compiled by NDK Clang.

Linkora's source patches disable V8 trap handling for cross-build host tools
and enable `CPU_NO_SIMD` for Android in both bundled zlib copies. Without these
patches, the build leaves unresolved host trap-handler symbols and expects an
NDK `cpufeatures` implementation that it does not link.

The verified outputs are shared by the main app fallback and the standalone
runtime pack:

```text
runtime-payloads/node/jniLibs/arm64-v8a/libnode_runtime.so
runtime-payloads/node/jniLibs/arm64-v8a/libc++_shared.so
runtime-payloads/node/assets/node/npm.zip
```

The runtime dynamically links the Android NDK C++ runtime. The build script
also installs and strips `libc++_shared.so` beside Node.js and copies the NDK
toolchain notice into the app's bundled licenses.

Verified ARM64 SHA-256 values:

```text
libnode_runtime.so  c03a6a63832dc27dec14d6879a194242c7f2142301471c18d67354fe137d2b6a
libc++_shared.so     cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c
```

At runtime, Linkora creates an app-private `node` symbolic link that points to
this signed executable and prepends its directory to `PATH`. This lets npm
lifecycle scripts invoke `node` without copying executable code into writable
storage.

The npm archive is generated separately from npm's verified registry tarball:

```sh
./scripts/build-npm-bundle.sh
```

The bundler normalizes timestamps and entry order, so repeated builds produce
the same archive SHA-256.

Before updating the bundled runtime, verify it on a real Android device with
`--version`, core-module, HTTPS, and local HTTP-server smoke tests. Record the
new binary SHA-256 in the runtime resolver.
