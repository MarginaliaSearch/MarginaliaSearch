package nu.marginalia.test;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;

public class TestMigrationLoader {

    /** Use flyway to load migrations from the classpath and run them against the database. */
    public static void flywayMigration(HikariDataSource dataSource) {
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    /** Load specific migrations from the classpath and run them against the database. */
    public static void loadMigrations(HikariDataSource dataSource, String... migrations) {
        for (var migration : migrations) {
            loadMigration(dataSource, migration);
        }
    }

    /** Load specific migration from the classpath and run them against the database. */
    public static void loadMigration(HikariDataSource dataSource, String migration) {
        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(migration),
                "Could not load migration script " + migration);
             var conn = dataSource.getConnection();
             var stmt = conn.createStatement()
        ) {
            String script = new String(resource.readAllBytes());
            String[] cmds = script.split("\\s*;\\s*");
            for (String cmd : cmds) {
                if (cmd.isBlank())
                    continue;
                System.out.println(cmd);
                stmt.executeUpdate(cmd);
            }
        }
        catch (IOException | SQLException ex) {
            Assertions.fail("Failed to load migration " + migration, ex);
        }
    }
}
