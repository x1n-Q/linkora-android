# Linkora for Android

Lightweight Android companion for Linkora, built with Kotlin and Jetpack Compose.

## Current slice

- Responsive Linkora onboarding UI
- Static HTML, Node.js, PHP, and Python project templates
- Browser-based device authorization through Android Custom Tabs
- Automatic approval polling and session restoration
- Android Keystore-encrypted access-token storage
- Linkora brand colors and Android launcher icon

Sign-in uses Linkora's one-time device authorization flow. The app opens the
approval page on `linkora.top`, never receives the account password, and exchanges
the approved request for a revocable device token.

## Build requirements

- Android SDK 37 and Build Tools 36.0.0
- JDK 17
- Gradle 9.6 through the included wrapper configuration

Run `./gradlew :app:assembleDebug` and install the generated APK on an Android 8.0
or newer device. Android Studio is optional.
