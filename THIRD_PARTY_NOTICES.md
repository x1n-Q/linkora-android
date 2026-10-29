# Third-party notices

Linkora for Android is distributed under the MIT License. The application also
contains or depends on the following free and open source software.

## cloudflared

Linkora for Android includes `cloudflared` 2026.10.4, compiled from the
official Cloudflare source with the Android NDK for ARM64. It is packaged as a
native executable library so Android can verify and execute it from the app
install.

- Project: https://github.com/cloudflare/cloudflared
- Copyright: Cloudflare, Inc.
- License: Apache License 2.0
- Bundled license: `app/src/main/assets/licenses/Apache-2.0.txt`

`cloudflared` is started only after the user chooses to connect a Linkora
hostname. Automatic updates are disabled so the packaged binary remains the
verified version shipped with the app.

## Node.js

Linkora for Android includes Node.js 24.22.1, compiled from the official Node.js
source for Android ARM64. The runtime is packaged as signed native executable
code and is started only for a user-selected Node.js project.

- Project: https://github.com/nodejs/node
- Copyright: Node.js contributors
- License: MIT and third-party licenses listed by the Node.js project
- Bundled license: `app/src/main/assets/licenses/Node.js-LICENSE.txt`

The first Android release omits optional ICU internationalization data to keep
the application smaller. Node.js on Android is maintained and tested by the
Linkora project because Android is not covered by Node.js upstream CI.

The Node.js executable dynamically links `libc++_shared.so` from Android NDK
28.2.13676358. The complete NDK toolchain notice is bundled at
`app/src/main/assets/licenses/Android-NDK-NOTICE.txt`.

## npm

Linkora for Android includes npm 11.19.1 from its verified npm registry source
archive. npm is extracted into app-private storage only when dependency
installation is requested.

- Project: https://github.com/npm/cli
- Copyright: npm, Inc. and contributors
- License: Artistic License 2.0 and bundled dependency licenses
- Bundled license: `app/src/main/assets/licenses/npm-LICENSE.txt`

## Application libraries

The Android application also uses these libraries from its Gradle dependencies:

| Component | Version | License | Source |
| --- | --- | --- | --- |
| NanoHTTPD | 2.3.1 | BSD-3-Clause | https://github.com/NanoHttpd/nanohttpd |
| AndroidX | See Gradle build files | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| Kotlin coroutines | 1.9.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
