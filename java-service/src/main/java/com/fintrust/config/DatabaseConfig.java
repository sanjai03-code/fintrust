package com.fintrust.config;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static String getEnv(String name, String defaultValue) {
        String val = System.getenv(name);
        if (val == null || val.trim().isEmpty()) {
            val = System.getProperty(name, defaultValue);
        }
        return val != null ? val.trim() : defaultValue;
    }

    public static Connection getConnection() throws SQLException {
        String driver = getEnv("DB_DRIVER", "pgsql");
        String host = getEnv("DB_HOST", "127.0.0.1");
        String port = getEnv("DB_PORT", "5432");
        String dbname = getEnv("DB_DATABASE", "fintrust");
        String user = getEnv("DB_USERNAME", "postgres");
        String pass = getEnv("DB_PASSWORD", "postgres");
        boolean isProduction = "production".equalsIgnoreCase(getEnv("APP_ENV", "development"));

        if (isProduction && !"pgsql".equalsIgnoreCase(driver)) {
            throw new SQLException("Production deployments must use PostgreSQL.");
        }

        if ("pgsql".equalsIgnoreCase(driver)) {
            try {
                String pgUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbname;
                Class.forName("org.postgresql.Driver");
                return DriverManager.getConnection(pgUrl, user, pass);
            } catch (Exception e) {
                if (isProduction) {
                    throw new SQLException("Unable to connect to the production PostgreSQL database.", e);
                }
                System.err.println("[DatabaseConfig] PostgreSQL connection failed: " + e.getMessage() + ". Attempting SQLite fallback.");
            }
        }

        // SQLite local database path
        String userDir = System.getProperty("user.dir");
        File dbFile = new File(userDir, "database/fintrust.db");
        if (!dbFile.exists()) {
            // Check parent directory
            File parentDb = new File(userDir, "../database/fintrust.db");
            if (parentDb.exists()) {
                dbFile = parentDb;
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (ClassNotFoundException e) {
            // If sqlite jar is not in classpath, rethrow SQL exception
            throw new SQLException("Database driver not found: " + e.getMessage());
        }
    }
}
