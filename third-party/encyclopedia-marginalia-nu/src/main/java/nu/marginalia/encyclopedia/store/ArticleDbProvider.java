package nu.marginalia.encyclopedia.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ArticleDbProvider {
    private final Connection connection;

    public ArticleDbProvider(Path filename) throws SQLException {
        String sqliteDbString = "jdbc:sqlite:" + filename.toString();
        connection = DriverManager.getConnection(sqliteDbString);

        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS articles (
                        url TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        html BLOB NOT NULL,
                        urls BLOB NOT NULL,
                        disambigs BLOB NOT NULL
                    )
                    """);

        }
    }

    public Connection getConnection() {
        return connection;
    }
}
