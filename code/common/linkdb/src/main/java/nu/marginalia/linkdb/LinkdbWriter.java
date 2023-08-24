package nu.marginalia.linkdb;

import nu.marginalia.linkdb.model.LdbUrlDetail;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class LinkdbWriter {

    private final Connection connection;

    public LinkdbWriter(Path outputFile) throws SQLException {
        String connStr = "jdbc:sqlite:" + outputFile.toString();
        connection = DriverManager.getConnection(connStr);

        try (var stream = ClassLoader.getSystemResourceAsStream("db/linkdb-document.sql");
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

    public void add(LdbUrlDetail ldbUrlDetail) throws SQLException {
        add(List.of(ldbUrlDetail));
    }

    public void add(List<LdbUrlDetail> ldbUrlDetail) throws SQLException {

        try (var stmt = connection.prepareStatement("""
                INSERT OR IGNORE INTO DOCUMENT(ID, URL, TITLE, DESCRIPTION, WORDS_TOTAL, FORMAT, FEATURES, DATA_HASH, QUALITY, PUB_YEAR)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {

            int i = 0;
            for (var document : ldbUrlDetail) {
                var url = document.url();

                stmt.setLong(1, document.urlId());
                stmt.setString(2, url.toString());

                stmt.setString(3, document.title());
                stmt.setString(4, document.description());
                stmt.setInt(5, document.wordsTotal());
                stmt.setString(6, document.format());
                stmt.setInt(7, document.features());
                stmt.setLong(8, document.dataHash());
                stmt.setDouble(9, document.urlQuality());
                if (document.pubYear() == null) {
                    stmt.setNull(10, Types.INTEGER);
                } else {
                    stmt.setInt(10, document.pubYear());
                }

                stmt.addBatch();

                if (++i > 1000) {
                    stmt.executeBatch();
                    i = 0;
                }
            }

            if (i != 0) stmt.executeBatch();
        }
    }

    public void close() throws SQLException {
        connection.close();
    }
}
