package nu.marginalia.search;

import nu.marginalia.test.AbstractDockerServiceTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the search-service-legacy Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class SearchLegacyDockerTest extends AbstractDockerServiceTest {

    public SearchLegacyDockerTest() {
        super("search-service-legacy");
    }

    /** The Docker image is named search-service-legacy but the service
     *  registers as search-service (ServiceId.Search), so we query for
     *  that name instead.
     */
    @Override
    @Test
    public void svcStartEventLoggedInDatabase() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM SERVICE_EVENTLOG WHERE SERVICE_BASE = ? AND EVENT_TYPE = 'SVC-START'")
        ) {
            stmt.setString(1, "search-service");
            var rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0,
                    "Expected at least one SVC-START event for search-service");
        }
    }
}
