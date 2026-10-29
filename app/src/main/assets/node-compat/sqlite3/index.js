'use strict';

const fs = require('node:fs');
const { EventEmitter } = require('node:events');
const { DatabaseSync } = require('node:sqlite');

const OPEN_READONLY = 1;
const OPEN_READWRITE = 2;
const OPEN_CREATE = 4;
const cachedConnections = new Map();

function splitParameters(values) {
  const parameters = [...values];
  const callback = typeof parameters.at(-1) === 'function' ? parameters.pop() : null;
  if (parameters.length === 1 && Array.isArray(parameters[0])) {
    return { parameters: parameters[0], callback };
  }
  return { parameters, callback };
}

function callLater(callback, receiver, ...values) {
  if (typeof callback !== 'function') return;
  queueMicrotask(() => callback.apply(receiver, values));
}

function handleError(error, callback, receiver) {
  if (callback) {
    callLater(callback, receiver, error);
    return true;
  }
  return false;
}

function normalizeRow(row) {
  if (row === undefined || Array.isArray(row)) return row;
  return { ...row };
}

class Statement extends EventEmitter {
  constructor(database, source, callback) {
    super();
    this.database = database;
    this.sql = source;
    this.lastID = 0;
    this.changes = 0;
    this._boundParameters = null;
    this._finalized = false;
    try {
      this._statement = database._database.prepare(source);
      callLater(callback, this, null);
    } catch (error) {
      callLater(callback, this, error);
      if (!callback) throw error;
    }
  }

  bind(...values) {
    const { parameters, callback } = splitParameters(values);
    this._boundParameters = parameters;
    callLater(callback, this, null);
    return this;
  }

  reset(callback) {
    callLater(callback, this, null);
    return this;
  }

  finalize(callback) {
    this._finalized = true;
    this._statement = null;
    callLater(callback, this, null);
    return this;
  }

  run(...values) {
    const { parameters, callback } = splitParameters(values);
    try {
      const result = this._activeStatement().run(...this._parameters(parameters));
      this.lastID = result.lastInsertRowid;
      this.changes = result.changes;
      callLater(callback, this, null);
    } catch (error) {
      if (!handleError(error, callback, this)) throw error;
    }
    return this;
  }

  get(...values) {
    const { parameters, callback } = splitParameters(values);
    try {
      const row = normalizeRow(
        this._activeStatement().get(...this._parameters(parameters)),
      );
      callLater(callback, this, null, row);
    } catch (error) {
      if (!handleError(error, callback, this)) throw error;
    }
    return this;
  }

  all(...values) {
    const { parameters, callback } = splitParameters(values);
    try {
      const rows = this._activeStatement()
        .all(...this._parameters(parameters))
        .map(normalizeRow);
      callLater(callback, this, null, rows);
    } catch (error) {
      if (!handleError(error, callback, this)) throw error;
    }
    return this;
  }

  each(...values) {
    const callbacks = [];
    while (typeof values.at(-1) === 'function') callbacks.unshift(values.pop());
    const rowCallback = callbacks[0] ?? null;
    const complete = callbacks[1] ?? null;
    const { parameters } = splitParameters(values);
    try {
      const rows = this._activeStatement()
        .all(...this._parameters(parameters))
        .map(normalizeRow);
      rows.forEach((row) => callLater(rowCallback, this, null, row));
      callLater(complete, this, null, rows.length);
    } catch (error) {
      if (rowCallback) callLater(rowCallback, this, error);
      callLater(complete, this, error, 0);
      if (!rowCallback && !complete) throw error;
    }
    return this;
  }

  _parameters(parameters) {
    return parameters.length === 0 && this._boundParameters !== null
      ? this._boundParameters
      : parameters;
  }

  _activeStatement() {
    if (this._finalized || !this._statement) throw new Error('Statement is already finalized.');
    return this._statement;
  }
}

class Database extends EventEmitter {
  constructor(filename, mode, callback) {
    super();
    if (typeof mode === 'function') {
      callback = mode;
      mode = OPEN_READWRITE | OPEN_CREATE;
    }
    mode ??= OPEN_READWRITE | OPEN_CREATE;
    this.filename = filename;
    this.mode = mode;
    this.open = false;

    try {
      const readOnly = Boolean(mode & OPEN_READONLY) && !Boolean(mode & OPEN_READWRITE);
      if (!readOnly && !Boolean(mode & OPEN_CREATE) && !fs.existsSync(filename)) {
        throw new Error(`SQLITE_CANTOPEN: database file does not exist: ${filename}`);
      }
      this._database = new DatabaseSync(filename, {
        readOnly,
        timeout: 5_000,
        enableDoubleQuotedStringLiterals: true,
        allowExtension: false,
      });
      this.open = true;
      callLater(callback, this, null);
    } catch (error) {
      callLater(callback, this, error);
      if (!callback) throw error;
    }
  }

  run(source, ...values) {
    const statement = new Statement(this, source);
    statement.run(...values);
    return this;
  }

  get(source, ...values) {
    const statement = new Statement(this, source);
    statement.get(...values);
    return this;
  }

  all(source, ...values) {
    const statement = new Statement(this, source);
    statement.all(...values);
    return this;
  }

  each(source, ...values) {
    const statement = new Statement(this, source);
    statement.each(...values);
    return this;
  }

  exec(source, callback) {
    try {
      this._database.exec(source);
      callLater(callback, this, null);
    } catch (error) {
      if (!handleError(error, callback, this)) throw error;
    }
    return this;
  }

  prepare(source, ...values) {
    const callback = typeof values.at(-1) === 'function' ? values.pop() : null;
    const statement = new Statement(this, source, callback);
    if (values.length > 0) statement.bind(...values);
    return statement;
  }

  serialize(callback) {
    callback?.();
    return this;
  }

  parallelize(callback) {
    callback?.();
    return this;
  }

  configure() {
    return this;
  }

  interrupt() {
    return this;
  }

  loadExtension(_filename, callback) {
    const error = new Error('SQLite extensions are disabled in Linkora compatibility mode.');
    if (!handleError(error, callback, this)) throw error;
    return this;
  }

  close(callback) {
    try {
      if (this.open) this._database.close();
      this.open = false;
      callLater(callback, this, null);
    } catch (error) {
      if (!handleError(error, callback, this)) throw error;
    }
    return this;
  }
}

function verbose() {
  return module.exports;
}

function cachedDatabase(filename, mode, callback) {
  if (typeof mode === 'function') {
    callback = mode;
    mode = undefined;
  }
  const existing = cachedConnections.get(filename);
  if (existing?.open) {
    callLater(callback, existing, null);
    return existing;
  }
  const database = new Database(filename, mode, callback);
  cachedConnections.set(filename, database);
  return database;
}

module.exports = {
  Database,
  Statement,
  OPEN_READONLY,
  OPEN_READWRITE,
  OPEN_CREATE,
  cached: { Database: cachedDatabase, objects: cachedConnections },
  verbose,
};
