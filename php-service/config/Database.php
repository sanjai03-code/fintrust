<?php
declare(strict_types=1);

namespace FinTrust\Config;

use PDO;
use PDOException;

class Database {
    private static ?PDO $instance = null;

    public static function getConnection(): PDO {
        if (self::$instance !== null) {
            return self::$instance;
        }

        $root = dirname(__DIR__, 2);
        $driver = getenv('DB_DRIVER') ?: 'pgsql';
        $host = getenv('DB_HOST') ?: '127.0.0.1';
        $port = getenv('DB_PORT') ?: '5432';
        $dbname = getenv('DB_DATABASE') ?: 'fintrust';
        $user = getenv('DB_USERNAME') ?: 'postgres';
        $pass = getenv('DB_PASSWORD') ?: 'postgres';
        $sqlitePath = $root . '/database/fintrust.db';
        $isProduction = strtolower(getenv('APP_ENV') ?: 'development') === 'production';

        if ($isProduction && $driver !== 'pgsql') {
            throw new \RuntimeException('Production deployments must use PostgreSQL.');
        }

        if ($driver === 'pgsql') {
            try {
                $dsn = "pgsql:host={$host};port={$port};dbname={$dbname}";
                self::$instance = new PDO($dsn, $user, $pass, [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::ATTR_EMULATE_PREPARES => false,
                ]);
                return self::$instance;
            } catch (PDOException $e) {
                if ($isProduction) {
                    throw new \RuntimeException('Unable to connect to the production PostgreSQL database.', 0, $e);
                }
                error_log("PostgreSQL connection error: " . $e->getMessage() . ". Using SQLite fallback.");
            }
        }

        // SQLite Fallback
        if (!file_exists($sqlitePath)) {
            $sqliteDir = dirname($sqlitePath);
            if (!is_dir($sqliteDir)) {
                mkdir($sqliteDir, 0777, true);
            }
        }

        self::$instance = new PDO("sqlite:" . $sqlitePath, null, null, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC
        ]);
        self::$instance->exec("PRAGMA foreign_keys = ON;");

        return self::$instance;
    }
}
