'use strict';

const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const { createRequire } = require('node:module');
const os = require('node:os');
const path = require('node:path');

const BetterDatabase = require('../app/src/main/assets/node-compat/better-sqlite3');
const sqlite3 = require('../app/src/main/assets/node-compat/sqlite3');

const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'linkora-sqlite-'));

function testBetterSqlite3() {
  const database = new BetterDatabase(path.join(temporaryDirectory, 'better.sqlite'));
  database.pragma('journal_mode = WAL');
  database.exec('CREATE TABLE notes(id INTEGER PRIMARY KEY, title TEXT NOT NULL) STRICT');
  const insert = database.prepare('INSERT INTO notes(title) VALUES (?)');
  const insertMany = database.transaction((titles) => {
    titles.forEach((title) => insert.run(title));
  });
  insertMany(['First', 'Second']);
  assert.deepEqual(
    database.prepare('SELECT title FROM notes ORDER BY id').pluck().all(),
    ['First', 'Second'],
  );
  assert.equal(database.prepare('SELECT count(*) AS count FROM notes').get().count, 2);
  database.close();
}

async function testSqlite3() {
  const database = await new Promise((resolve, reject) => {
    const instance = new sqlite3.Database(
      path.join(temporaryDirectory, 'sqlite3.sqlite'),
      (error) => (error ? reject(error) : resolve(instance)),
    );
  });
  await new Promise((resolve, reject) => {
    database.exec(
      'CREATE TABLE notes(id INTEGER PRIMARY KEY, title TEXT NOT NULL) STRICT',
      (error) => (error ? reject(error) : resolve()),
    );
  });
  const inserted = await new Promise((resolve, reject) => {
    database.run('INSERT INTO notes(title) VALUES (?)', 'Compatible', function callback(error) {
      if (error) reject(error);
      else resolve({ id: this.lastID, changes: this.changes });
    });
  });
  assert.equal(inserted.changes, 1);
  const row = await new Promise((resolve, reject) => {
    database.get('SELECT title FROM notes WHERE id = ?', inserted.id, (error, value) => {
      if (error) reject(error);
      else resolve(value);
    });
  });
  assert.deepEqual(row, { title: 'Compatible' });
  await new Promise((resolve, reject) => {
    database.close((error) => (error ? reject(error) : resolve()));
  });
}

function testNpmFileAdapters() {
  const projectDirectory = path.join(temporaryDirectory, 'npm-project');
  const betterAdapter = path.resolve(
    __dirname,
    '../app/src/main/assets/node-compat/better-sqlite3',
  );
  const sqlite3Adapter = path.resolve(
    __dirname,
    '../app/src/main/assets/node-compat/sqlite3',
  );
  fs.mkdirSync(projectDirectory);
  fs.writeFileSync(
    path.join(projectDirectory, 'package.json'),
    JSON.stringify(
      {
        name: 'linkora-sqlite-install-test',
        private: true,
        dependencies: {
          'better-sqlite3': `file:${betterAdapter}`,
          sqlite3: `file:${sqlite3Adapter}`,
        },
      },
      null,
      2,
    ),
  );
  execFileSync('npm', ['install', '--no-audit', '--no-fund'], {
    cwd: projectDirectory,
    stdio: 'pipe',
  });
  const requireFromProject = createRequire(path.join(projectDirectory, 'package.json'));
  assert.equal(typeof requireFromProject('better-sqlite3'), 'function');
  assert.equal(typeof requireFromProject('sqlite3').Database, 'function');
}

async function main() {
  try {
    testBetterSqlite3();
    await testSqlite3();
    testNpmFileAdapters();
    console.log('SQLite compatibility adapters passed.');
  } finally {
    fs.rmSync(temporaryDirectory, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
