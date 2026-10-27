# Node.js runtime plan

Linkora runs Node.js directly on Android without Termux. The runtime is built
for Android ARM64, signed inside the app, and launched as a child process by a
foreground service.

## Runtime

- Node.js 24 LTS, built with Android NDK r28 for API 26 and 16 KB pages
- One active project at a time on `127.0.0.1:8787`
- Runtime, logs, restart, and stop controls managed by Linkora
- Existing Linkora tunnel publishes the same local port

## Projects

- Users select a real folder through Android Files
- Static projects continue to stream files directly from that folder
- Node projects are synchronized into app-private storage before execution
  because Node module resolution requires ordinary filesystem paths
- The selected Android Files folder remains the editable source of truth

## Dependencies

- Linkora bundles the npm CLI and installs dependencies into the private Node
  workspace
- Installing dependencies displays a warning that native modules and package
  scripts may not support Android
- Users may cancel or explicitly try the installation
- Install and runtime output is retained in the in-app log view

## Delivery sequence

1. Build and verify the Android Node runtime.
2. Add project detection and folder synchronization.
3. Add the foreground Node process service and status model.
4. Add npm extraction and dependency installation.
5. Add Node workspace controls and the native-module warning.
6. Integrate publishing, tests, documentation, and release metadata.
