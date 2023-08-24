package nu.marginalia.linkdb;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import gnu.trove.list.TLongList;
import nu.marginalia.linkdb.model.LdbUrlDetail;
import nu.marginalia.model.EdgeUrl;
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

@Singleton
public class LinkdbReader {
    private Path dbFile;
    private volatile Connection connection;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public LinkdbReader(@Named("linkdb-file") Path dbFile) throws SQLException {
        this.dbFile = dbFile;

        if (Files.exists(dbFile)) {
            try {
                connection = createConnection();
            }
            catch (SQLException ex) {
                connection = null;
                logger.error("Failed to load linkdb file", ex);
            }
        }
        else {
            logger.warn("No linkdb file {}", dbFile);
        }
    }

    private Connection createConnection() throws SQLException {
        String connStr = "jdbc:sqlite:" + dbFile.toString();
        return DriverManager.getConnection(connStr);
    }

    public void switchInput(Path newDbFile) throws IOException, SQLException {
        if (connection != null) {
            connection.close();
        }

        Files.move(newDbFile, dbFile, StandardCopyOption.REPLACE_EXISTING);

        connection = createConnection();
    }

    public List<LdbUrlDetail> getUrlDetails(TLongList ids) throws SQLException {
        List<LdbUrlDetail> ret = new ArrayList<>(ids.size());

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
                    ret.add(new LdbUrlDetail(
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
