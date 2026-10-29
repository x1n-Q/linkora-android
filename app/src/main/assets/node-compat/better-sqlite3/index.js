'use strict';

const fs = require('node:fs');
const { DatabaseSync } = require('node:sqlite');

class LinkoraSqliteError extends Error {
  constructor(message, code = 'SQLITE_ERROR') {
    super(message);
    this.name = 'SqliteError';
    this.code = code;
  }
}

function normalizeParameters(parameters) {
  if (parameters.length === 1 && Array.isArray(parameters[0])) {
    return parameters[0];
  }
  return parameters;
}

class LinkoraStatement {
  constructor(owner, source, statement) {
    this.database = owner;
    this.source = source;
    this.reader = /^\s*(SELECT|PRAGMA|WITH|EXPLAIN)\b/i.test(source);
    this.readonly = /^\s*(SELECT|PRAGMA|WITH|EXPLAIN)\b/i.test(source);
    this._statement = statement;
    this._boundParameters = null;
    this._pluck = false;
    this._raw = false;
    this._safeIntegers = owner._safeIntegers;
  }

  bind(...parameters) {
    this._boundParameters = normalizeParameters(parameters);
    return this;
  }

  run(...parameters) {
    this._applyModes();
    return this._statement.run(...this._parameters(parameters));
  }

  get(...parameters) {
    this._applyModes();
    return this._transformRow(this._statement.get(...this._parameters(parameters)));
  }

  all(...parameters) {
    this._applyModes();
    return this._statement
      .all(...this._parameters(parameters))
      .map((row) => this._transformRow(row));
  }

  iterate(...parameters) {
    this._applyModes();
    const iterator = this._statement.iterate(...this._parameters(parameters));
    const transform = (row) => this._transformRow(row);
    return (function* transformedRows() {
      for (const row of iterator) yield transform(row);
    })();
  }

  pluck(enabled = true) {
    this._pluck = Boolean(enabled);
    return this;
  }

  raw(enabled = true) {
    this._raw = Boolean(enabled);
    return this;
  }

  expand(enabled = true) {
    if (enabled) {
      throw new LinkoraSqliteError(
        'better-sqlite3 expand() is not available in Linkora compatibility mode.',
        'LINKORA_SQLITE_UNSUPPORTED',
      );
    }
    return this;
  }

  safeIntegers(enabled = true) {
    this._safeIntegers = Boolean(enabled);
    return this;
  }

  columns() {
    return this._statement.columns();
  }

  _parameters(parameters) {
    if (parameters.length === 0 && this._boundParameters !== null) {
      return this._boundParameters;
    }
    return normalizeParameters(parameters);
  }

  _applyModes() {
    this._statement.setReadBigInts(this._safeIntegers);
    this._statement.setReturnArrays(this._raw || this._pluck);
  }

  _transformRow(row) {
    if (row === undefined) return row;
    if (!this._pluck) {
      return Array.isArray(row) ? row : { ...row };
    }
    if (Array.isArray(row)) return row[0];
    return row[Object.keys(row)[0]];
  }
}

class LinkoraDatabase {
  constructor(filename = ':memory:', options = {}) {
    if (typeof options === 'string') options = {};
    if (options.fileMustExist && filename !== ':memory:' && !fs.existsSync(filename)) {
      throw new LinkoraSqliteError(`Database file does not exist: ${filename}`, 'SQLITE_CANTOPEN');
    }

    this.name = filename;
    this.memory = filename === ':memory:';
    this.readonly = Boolean(options.readonly);
    this.open = true;
    this.inTransaction = false;
    this._safeIntegers = false;
    this._transactionCounter = 0;
    this._database = new DatabaseSync(filename, {
      readOnly: this.readonly,
      timeout: options.timeout ?? 5_000,
      enableDoubleQuotedStringLiterals: true,
      allowExtension: false,
    });
    if (typeof options.verbose === 'function') this._verbose = options.verbose;
  }

  prepare(source) {
    this._assertOpen();
    this._verbose?.(source);
    return new LinkoraStatement(this, source, this._database.prepare(source));
  }

  exec(source) {
    this._assertOpen();
    this._verbose?.(source);
    this._database.exec(source);
    return this;
  }

  pragma(source, options = {}) {
    const rows = this.prepare(`PRAGMA ${source}`).all();
    if (!options.simple) return rows;
    const first = rows[0];
    return first === undefined ? undefined : first[Object.keys(first)[0]];
  }

  transaction(callback) {
    if (typeof callback !== 'function') throw new TypeError('Expected a transaction function.');
    const run = (mode, receiver, parameters) => {
      this._assertOpen();
      const nested = this.inTransaction;
      const savepoint = `linkora_${++this._transactionCounter}`;
      this.exec(nested ? `SAVEPOINT ${savepoint}` : `BEGIN ${mode}`);
      if (!nested) this.inTransaction = true;
      try {
        const result = callback.apply(receiver, parameters);
        this.exec(nested ? `RELEASE SAVEPOINT ${savepoint}` : 'COMMIT');
        return result;
      } catch (error) {
        try {
          this.exec(nested ? `ROLLBACK TO SAVEPOINT ${savepoint}` : 'ROLLBACK');
        } finally {
          if (nested) this.exec(`RELEASE SAVEPOINT ${savepoint}`);
        }
        throw error;
      } finally {
        if (!nested) this.inTransaction = false;
      }
    };
    const transaction = function linkoraTransaction(...parameters) {
      return run('DEFERRED', this, parameters);
    };
    transaction.deferred = function deferred(...parameters) {
      return run('DEFERRED', this, parameters);
    };
    transaction.immediate = function immediate(...parameters) {
      return run('IMMEDIATE', this, parameters);
    };
    transaction.exclusive = function exclusive(...parameters) {
      return run('EXCLUSIVE', this, parameters);
    };
    return transaction;
  }

  function(name, options, callback) {
    if (typeof options === 'function') {
      callback = options;
      options = {};
    }
    this._database.function(name, options ?? {}, callback);
    return this;
  }

  aggregate(name, options) {
    this._database.aggregate(name, options);
    return this;
  }

  defaultSafeIntegers(enabled = true) {
    this._safeIntegers = Boolean(enabled);
    return this;
  }

  unsafeMode() {
    return this;
  }

  loadExtension() {
    throw new LinkoraSqliteError(
      'SQLite extensions are disabled in Linkora compatibility mode.',
      'LINKORA_SQLITE_UNSUPPORTED',
    );
  }

  backup() {
    return Promise.reject(
      new LinkoraSqliteError(
        'better-sqlite3 backup() is not available in Linkora compatibility mode.',
        'LINKORA_SQLITE_UNSUPPORTED',
      ),
    );
  }

  close() {
    if (!this.open) return this;
    this._database.close();
    this.open = false;
    return this;
  }

  _assertOpen() {
    if (!this.open) throw new LinkoraSqliteError('The database connection is not open.');
  }
}

function Database(filename, options) {
  return new LinkoraDatabase(filename, options);
}

Database.prototype = LinkoraDatabase.prototype;
Database.SqliteError = LinkoraSqliteError;
Database.default = Database;

module.exports = Database;
