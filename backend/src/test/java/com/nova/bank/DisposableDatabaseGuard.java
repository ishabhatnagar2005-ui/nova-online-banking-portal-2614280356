package com.nova.bank;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Fail closed before destructive tests initialize and before every reset. */
public class DisposableDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String expected = expectedDatabase();
        String url = System.getenv("TEST_DATABASE_URL");
        if (url == null || url.isBlank() || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Set TEST_DATABASE_URL explicitly for the disposable database");
        }
        try (var connection = DriverManager.getConnection(url,
                System.getenv().getOrDefault("TEST_DB_USERNAME", "agent"),
                System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
             var statement = connection.createStatement();
             var result = statement.executeQuery("select current_database()")) {
            if (!result.next() || !expected.equals(result.getString(1))) {
                throw new IllegalStateException("Connected database does not match TEST_DATABASE_NAME");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot verify disposable database target", e);
        }
    }

    private static String expectedDatabase() {
        String name = System.getenv("TEST_DATABASE_NAME");
        if (!"1".equals(System.getenv("TEST_DISPOSABLE")) || name == null
                || !name.matches("[a-zA-Z0-9_]+_test")) {
            throw new IllegalStateException("Set TEST_DISPOSABLE=1 and TEST_DATABASE_NAME to the dedicated disposable database name ending in _test");
        }
        return name;
    }

    /** Check the server-reported database name immediately before a destructive operation. */
    public static void verify(JdbcTemplate jdbc) {
        String expected = expectedDatabase();
        String actual = jdbc.queryForObject("select current_database()", String.class);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Connected database does not match TEST_DATABASE_NAME");
        }
    }
}
