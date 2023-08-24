package nu.marginalia.linkdb;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import gnu.trove.list.TLongList;
import nu.marginalia.linkdb.model.UrlDetail;
import nu.marginalia.linkdb.model.UrlProtocol;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

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
    Path dbFile;
    volatile Connection connection;

    @Inject
    public LinkdbReader(@Named("linkdb-file") Path dbFile) throws SQLException {
        this.dbFile = dbFile;
        connection = createConnection();
    }

    private Connection createConnection() throws SQLException {
        String connStr = "jdbc:sqlite:" + dbFile.toString();
        return DriverManager.getConnection(connStr);
    }

    public void switchInput(Path newDbFile) throws IOException, SQLException {
        connection.close();

        Files.move(newDbFile, dbFile, StandardCopyOption.REPLACE_EXISTING);

        connection = createConnection();
    }

    public List<UrlDetail> getUrlDetails(TLongList ids) throws SQLException {
        List<UrlDetail> ret = new ArrayList<>(ids.size());

        if (connection.isClosed()) {
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
                    ret.add(new UrlDetail(
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
