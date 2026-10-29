# SQLite compatibility

Linkora's bundled Node.js 24 runtime includes `node:sqlite`. Projects that use
the built-in module run without an npm database driver. Linkora also provides
compatibility adapters for common projects that declare `better-sqlite3` or
`sqlite3` as a direct dependency.

## Runtime behavior

- The selected project source is copied into a private runtime workspace.
- Every selected project has a stable, separate workspace.
- Source updates overwrite their matching runtime files.
- Source files removed by the developer are removed from the runtime copy.
- Runtime-created files such as SQLite databases, uploads, and logs are kept.
- `node_modules` and Linkora metadata are never copied back to the selected
  project folder.

During `npm install`, Linkora temporarily points supported SQLite dependencies
to adapters bundled with the application. It restores `package.json` and npm
lock files immediately afterward. This substitution happens only in the
private runtime copy; the folder selected through Android Files is unchanged.

## Supported surface

The adapters cover the common APIs used by small web applications and many
framework database layers:

- opening and closing file or in-memory databases
- SQL execution and prepared statements
- positional, array, and named parameters
- row reads and inserts with change metadata
- transactions
- callback-style `sqlite3` queries
- `better-sqlite3` `get`, `all`, `run`, `iterate`, `pluck`, and `raw` modes
- user-defined functions and aggregates exposed by `node:sqlite`

## Intentional limitations

Compatibility mode does not load native SQLite extensions. The
`better-sqlite3` `expand()` and `backup()` APIs are not currently implemented.
Projects that depend on custom native bindings, Prisma engines, or undocumented
driver behavior still require a dedicated Android build.

Linkora reports compatibility mode in the project screen and runtime log. An
unsupported operation fails with an explicit error instead of silently changing
the query.

## Verification

Run the host-side adapter and local npm installation tests with Node.js 24:

```sh
node scripts/test-node-sqlite-compat.js
```

The bundled Android runtime must also pass file persistence, WAL, transaction,
and FTS5 smoke tests on a physical ARM64 device before release.
