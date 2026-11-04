<?php
/**
 * FinTrust - Database Migration Runner
 * Supports PostgreSQL (default) and SQLite (zero-config local fallback)
 */

declare(strict_types=1);

$projectRoot = dirname(__DIR__);
$envFile = $projectRoot . '/.env';

if (file_exists($envFile)) {
    $lines = file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    foreach ($lines as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#')) {
            continue;
        }
        $parts = explode('=', $line, 2);
        if (count($parts) === 2) {
            $key = trim($parts[0]);
            $val = trim($parts[1]);
            $_ENV[$key] = $val;
            putenv("$key=$val");
        }
    }
}

$driver = getenv('DB_DRIVER') ?: 'pgsql';
$host = getenv('DB_HOST') ?: '127.0.0.1';
$port = getenv('DB_PORT') ?: '5432';
$dbname = getenv('DB_DATABASE') ?: 'fintrust';
$user = getenv('DB_USERNAME') ?: 'postgres';
$pass = getenv('DB_PASSWORD') ?: 'postgres';
$sqlitePath = $projectRoot . '/database/fintrust.db';
$isProduction = strtolower(getenv('APP_ENV') ?: 'development') === 'production';

$pdo = null;

echo "=== FinTrust Database Migration Runner ===\n";
echo "Configured Driver: {$driver}\n";

if ($isProduction && $driver !== 'pgsql') {
    throw new RuntimeException('Production deployments must use PostgreSQL.');
}

if ($driver === 'pgsql') {
    try {
        $dsn = "pgsql:host={$host};port={$port};dbname={$dbname}";
        $pdo = new PDO($dsn, $user, $pass, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC
        ]);
        echo "[OK] Connected to PostgreSQL at {$host}:{$port}/{$dbname}\n";
    } catch (PDOException $e) {
        if ($isProduction) {
            throw new RuntimeException('Unable to connect to the production PostgreSQL database.', 0, $e);
        }
        echo "[WARNING] PostgreSQL connection failed: " . $e->getMessage() . "\n";
        echo "[INFO] Seamlessly falling back to local SQLite database at: {$sqlitePath}\n";
        $driver = 'sqlite';
    }
}

if ($driver === 'sqlite') {
    $sqliteDir = dirname($sqlitePath);
    if (!is_dir($sqliteDir)) {
        mkdir($sqliteDir, 0777, true);
    }
    $pdo = new PDO("sqlite:" . $sqlitePath, null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC
    ]);
    $pdo->exec("PRAGMA foreign_keys = ON;");
    echo "[OK] Connected to SQLite database.\n";
}

$migrationFiles = [
    $projectRoot . '/database/migrations/001_create_organizations_and_users.sql',
    $projectRoot . '/database/migrations/002_create_transactions_and_ledger.sql',
    $projectRoot . '/database/migrations/003_create_risk_and_alerts.sql',
    $projectRoot . '/database/migrations/004_create_audit_and_documents.sql'
];

foreach ($migrationFiles as $filePath) {
    if (!file_exists($filePath)) {
        echo "[ERROR] Missing migration file: {$filePath}\n";
        continue;
    }
    $sql = file_get_contents($filePath);
    echo "Running migration: " . basename($filePath) . "... ";
    
    if ($driver === 'sqlite') {
        // Adapt PostgreSQL specific DDL syntax to SQLite
        $sql = preg_replace('/\bSERIAL PRIMARY KEY\b/i', 'INTEGER PRIMARY KEY AUTOINCREMENT', $sql);
        $sql = preg_replace('/\bTIMESTAMP WITH TIME ZONE\b/i', 'DATETIME', $sql);
        $sql = preg_replace('/SELECT setval\(.*?\);/i', '', $sql);
    }

    try {
        $pdo->exec($sql);
        echo "[SUCCESS]\n";
    } catch (PDOException $e) {
        if ($isProduction) {
            throw $e;
        }
        echo "[ERROR] " . $e->getMessage() . "\n";
    }
}

// Demo credentials and records are only seeded in production when explicitly enabled.
$seedDemoData = filter_var(
    getenv('SEED_DEMO_DATA') ?: ($isProduction ? 'false' : 'true'),
    FILTER_VALIDATE_BOOLEAN
);
$seedFile = $projectRoot . '/database/seed_data.sql';
if ($seedDemoData && file_exists($seedFile)) {
    echo "Seeding initial demonstration records... ";
    $seedSql = file_get_contents($seedFile);
    if ($driver === 'sqlite') {
        $seedSql = preg_replace('/SELECT setval\(.*?\);/i', '', $seedSql);
        $seedSql = preg_replace('/\+00/i', '', $seedSql);
    }
    try {
        $pdo->exec($seedSql);
        echo "[SUCCESS]\n";
    } catch (PDOException $e) {
        if ($isProduction) {
            throw $e;
        }
        echo "[NOTE/ALREADY SEEDED] " . $e->getMessage() . "\n";
    }
}

echo "=== Migration Complete! ===\n";
