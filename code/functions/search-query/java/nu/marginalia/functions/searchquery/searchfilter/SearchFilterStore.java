package nu.marginalia.functions.searchquery.searchfilter;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import static nu.marginalia.api.searchquery.model.SearchFilterDefaults.SYSTEM_USER_ID;

public class SearchFilterStore {
    private final HikariDataSource dataSource;
    private final SearchFilterParser parser;

    private static final Logger logger = LoggerFactory.getLogger(SearchFilterStore.class);

    public SearchFilterStore(HikariDataSource dataSource, SearchFilterParser parser) {
        this.dataSource = dataSource;
        this.parser = parser;
    }

    public void loadDefaultConfigs() {
        for (SearchFilterDefaults defaultConfig : SearchFilterDefaults.values()) {
            try (var resourceStream = ClassLoader.getSystemResourceAsStream("filters" + "/" + defaultConfig.fileName)) {
                if (resourceStream == null) {
                    logger.error("Missing default config spec {}", defaultConfig.fileName);
                    continue;
                }

                String xml = new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);

                parser.parse(SYSTEM_USER_ID, defaultConfig.name(), xml);

                saveFilter(SYSTEM_USER_ID, defaultConfig.name(), xml);
            } catch (SearchFilterParser.SearchFilterParserException e) {
                logger.error("Default config spec {} failed to parse, refusing to insert", defaultConfig.name(), e);
            } catch (IOException | SQLException e) {
                logger.error("Error when setting up default search filter configs", e);
            }
        }
    }

    public Optional<SearchFilterSpec> getFilter(String userId, String specName) {
        String xml;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                     SELECT SPEC, ID
                     FROM SEARCH_FILTER
                     WHERE USER_ID=? AND NAME=?
                     """);
             PreparedStatement updateAccess = conn.prepareStatement("""
                     UPDATE SEARCH_FILTER
                     SET LAST_ACCESSED=CURRENT_TIMESTAMP
                     WHERE ID=?
                     """)
        ) {
            query.setString(1, userId);
            query.setString(2, specName);

            var rs = query.executeQuery();
            if (rs.next()) {
                updateAccess.setLong(1, rs.getLong("ID"));
                updateAccess.executeUpdate();

                xml = rs.getString("SPEC");
            } else {
                return Optional.empty();
            }
        } catch (SQLException ex) {
            logger.error("Failed to execute query", ex);
            return Optional.empty();
        }

        try {
            return Optional.of(parser.parse(userId, specName, xml));
        } catch (SearchFilterParser.SearchFilterParserException ex) {
            logger.error("Failed to parse query", ex);
            return Optional.empty();
        }
    }

    public void saveFilter(String userId, String name, String spec) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement update = conn.prepareStatement("""
                        REPLACE INTO SEARCH_FILTER (USER_ID, NAME, SPEC)
                        VALUES (?, ?, ?)
                        """))
        {
            update.setString(1, userId);
            update.setString(2, name);
            update.setString(3, spec);

            update.executeUpdate();
        }
    }
}
