# Release signing

Linkora and every official runtime pack use one production signing identity.
The certificate SHA-256 fingerprint is:

```text
30938a07ba42ca042033a78ac75f1730a5463d780e3d9a9119a762fa540c222d
```

The private key and passwords must never enter the repository. The local build
expects the keystore at `~/.local/share/linkora-android/signing/` and reads its
credentials from the mode-0600 file `~/.config/linkora-android/signing.env`.
Build every signed APK with:

```sh
./scripts/build-signed-release.sh
```

The Gradle release variants remain unsigned when those environment variables
are absent. This preserves a clean build path for F-Droid, which performs its
own reproducible signing after building from public source.

Before publishing, run:

```sh
./scripts/package-mariadb-source.sh
./scripts/verify-release-artifacts.sh
```

Keep an encrypted copy of the keystore and its password in a separate password
manager or offline backup. Losing this key prevents Android from installing
future versions over an existing Linkora installation. Never replace the key
for an existing package name without a planned Android signing-key rotation.
