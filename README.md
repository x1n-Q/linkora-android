# Linkora for Android

Lightweight Android companion for Linkora, built with Kotlin and Jetpack Compose.

## Current slice

- Responsive Linkora onboarding UI
- Static HTML, Node.js, PHP, and Python project templates
- Browser-based sign-in entry through Android Custom Tabs
- Linkora brand colors and Android launcher icon

The current web sign-in opens `linkora.top/login`. Token exchange and the verified
App Link callback will be added with the matching Linkora API authorization
endpoints in the next authentication slice.

## Build requirements

- Android Studio with Android SDK 37
- JDK 17
- Gradle 9.6 through the included wrapper configuration

Open the project in Android Studio, let Gradle sync, and run the `app`
configuration on an Android 8.0 or newer device.
