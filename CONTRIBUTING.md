# Contributing to Linkora Mobile

Thank you for improving Linkora Mobile. Contributions should keep the app
local-first, understandable to new users, and usable without a Linkora account.

## Before starting

- Search existing issues before opening a new one.
- Keep each pull request focused on one problem or feature.
- Discuss large architecture changes before implementing them.
- Do not include private keys, signing files, access tokens, or production
  credentials in issues, commits, logs, or screenshots.

## Development setup

Requirements:

- JDK 17
- Android SDK 37
- Android Build Tools 36.0.0
- An Android 8.0 or later device or emulator

Build the main app:

```bash
./gradlew :app:assembleDebug
```

Run the main validation tasks before submitting a pull request:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Changes to a Runtime Pack should also build and test the affected module. Native
payload changes must document their upstream source, license, reproducible build
steps, SHA-256 checksum, supported ABI, and physical-device test results.

## Pull requests

A pull request should include:

- a clear description of the user-facing problem;
- the implementation approach;
- tests performed and the Android devices or emulators used;
- screenshots for visible interface changes; and
- updated documentation when behavior, compatibility, or setup changes.

Use concise commit messages that describe the change. Avoid mixing formatting,
unrelated refactors, generated files, or release binaries into a feature commit.

## Design principles

- Standalone use must remain a first-class workflow.
- Linkora accounts and addresses must remain optional.
- Runtime downloads require explicit user approval and visible progress.
- Important controls need clear labels, visible boundaries, and accessible
  touch targets.
- Keep technical explanations out of primary workflows unless the user needs
  them to make a decision.

## Security reports

Do not disclose vulnerabilities in a public issue. Follow
[SECURITY.md](SECURITY.md) so the problem can be reviewed before details are
published.
