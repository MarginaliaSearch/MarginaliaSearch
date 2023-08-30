package nu.marginalia.linkdb;

import nu.marginalia.linkdb.model.UrlStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class LinkdbStatusWriter {

    private final Connection connection;

    public LinkdbStatusWriter(Path outputFile) throws SQLException {
        String connStr = "jdbc:sqlite:" + outputFile.toString();
        connection = DriverManager.getConnection(connStr);

        try (var stream = ClassLoader.getSystemResourceAsStream("db/linkdb-status.sql");
             var stmt = connection.createStatement()
        ) {
            var sql = new String(stream.readAllBytes());
            stmt.executeUpdate(sql);

            // Disable synchronous writing as this is a one-off operation with no recovery
            stmt.execute("PRAGMA synchronous = OFF");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void add(List<UrlStatus> statuses) throws SQLException {
        try (var stmt = connection.prepareStatement("""
                INSERT OR IGNORE INTO STATUS(ID, URL, STATUS, DESCRIPTION)
                VALUES (?, ?, ?, ?)
                """)) {
            int count = 0;
            for (var status : statuses) {
                stmt.setLong(1, status.id());
                stmt.setString(2, status.url().toString());
                stmt.setString(3, status.status());
                if (status.description() == null) {
                    stmt.setNull(4, Types.VARCHAR);
                } else {
                    stmt.setString(4, status.description());
                }
                stmt.addBatch();
                if (++count > 1000) {
                    count = 0;
                    stmt.executeBatch();
                }
            }
            if (count != 0) {
                stmt.executeBatch();
            }
        }
    }

    public void close() throws SQLException {
        connection.close();
    }
}
