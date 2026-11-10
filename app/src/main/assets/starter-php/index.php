<?php
declare(strict_types=1);

$database = new PDO('sqlite:' . __DIR__ . '/linkora.sqlite');
$database->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$database->exec(
    'CREATE TABLE IF NOT EXISTS visits (' .
    'id INTEGER PRIMARY KEY AUTOINCREMENT, visited_at TEXT NOT NULL)'
);
$database->prepare('INSERT INTO visits (visited_at) VALUES (:visited_at)')->execute([
    ':visited_at' => gmdate('c'),
]);
$visits = (int) $database->query('SELECT COUNT(*) FROM visits')->fetchColumn();
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Linkora PHP</title>
    <link rel="stylesheet" href="/styles.css">
</head>
<body>
    <main>
        <p class="eyebrow">LINKORA PHP RUNTIME</p>
        <h1>Your PHP project is running.</h1>
        <p class="lead">
            This page is served by PHP <?= htmlspecialchars(PHP_VERSION, ENT_QUOTES) ?>
            directly from your Android phone.
        </p>
        <dl>
            <div><dt>Runtime</dt><dd>PHP <?= htmlspecialchars(PHP_VERSION, ENT_QUOTES) ?></dd></div>
            <div><dt>Database</dt><dd>PDO SQLite <?= htmlspecialchars(SQLite3::version()['versionString'], ENT_QUOTES) ?></dd></div>
            <div><dt>Visits</dt><dd><?= $visits ?></dd></div>
        </dl>
    </main>
</body>
</html>
