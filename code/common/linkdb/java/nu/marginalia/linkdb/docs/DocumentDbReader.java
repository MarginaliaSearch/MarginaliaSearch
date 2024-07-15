package nu.marginalia.linkdb.docs;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import gnu.trove.list.TLongList;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads the document database, which is a SQLite database
 * containing the URLs and metadata of the documents in the
 * index.
 * <p></p>
 * The database is created by the DocumentDbWriter class.
 * */
@Singleton
public class DocumentDbReader {
    private final Path dbFile;
    private volatile Connection connection;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DocumentDbReader(@Named("docdb-file") Path dbFile) throws SQLException {
        this.dbFile = dbFile;

        if (Files.exists(dbFile)) {
            connection = createConnection();
        }
        else {
            logger.warn("No docdb file {}", dbFile);
        }
    }

    private Connection createConnection() throws SQLException {
        try {
            String connStr = "jdbc:sqlite:" + dbFile.toString();
            return DriverManager.getConnection(connStr);
        }
        catch (SQLException ex) {
            logger.error("Failed to connect to link database " + dbFile, ex);
            return null;
        }
    }

    /** Switches the input database file to a new file.
     * <p></p>
     * This is used to switch over to a new database file
     * when the index is re-indexed.
     * */
    public void switchInput(Path newDbFile) throws IOException, SQLException {
        if (!Files.isRegularFile(newDbFile)) {
            logger.error("Source is not a file, refusing switch-over {}", newDbFile);
            return;
        }

        if (connection != null) {
            connection.close();
        }

        logger.info("Moving {} to {}", newDbFile, dbFile);

        Files.move(newDbFile, dbFile, StandardCopyOption.REPLACE_EXISTING);

        connection = createConnection();
    }

    /** Re-establishes the connection, useful in tests and not
     * much else */
    public void reconnect() throws SQLException {
        if (connection != null)
            connection.close();

        connection = createConnection();
    }

    /** Returns the URL details for the given document ids.
     * <p></p>
     * This is used to get the URL details for the search
     * results.
     * */
    public List<DocdbUrlDetail> getUrlDetails(TLongList ids) throws SQLException {
        List<DocdbUrlDetail> ret = new ArrayList<>(ids.size());

        if (connection == null ||
            connection.isClosed())
        {
            throw new RuntimeException("URL query temporarily unavailable due to database switch");
        }

        try (var stmt = connection.prepareStatement("""
                SELECT ID, URL, TITLE, DESCRIPTION, WORDS_TOTAL, FORMAT, FEATURES, DATA_HASH, QUALITY, PUB_YEAR
                FROM DOCUMENT WHERE ID = ?
                """)) {
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                stmt.setLong(1, id);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    var url = new EdgeUrl(rs.getString("URL"));
                    ret.add(new DocdbUrlDetail(
                            rs.getLong("ID"),
                            url,
                            rs.getString("TITLE"),
                            rs.getString("DESCRIPTION"),
                            rs.getDouble("QUALITY"),
                            rs.getString("FORMAT"),
                            rs.getInt("FEATURES"),
                            rs.getInt("PUB_YEAR"),
                            rs.getLong("DATA_HASH"),
                            rs.getInt("WORDS_TOTAL")
                    ));
                }
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return ret;
    }
}
