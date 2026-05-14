package nu.marginalia.browse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the EC_RANDOM_DOMAIN_SUGGESTIONS queue: user-submitted candidates
 * for inclusion in the random exploration set, awaiting operator review.
 */
@Singleton
public class RandomDomainSuggestionsDao {

    private static final DateTimeFormatter SUGGESTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HikariDataSource dataSource;

    @Inject
    public RandomDomainSuggestionsDao(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public enum SubmitOutcome {
        SUBMITTED,
        INELIGIBLE,
        DUPLICATE
    }

    /** A domain's standing with respect to the random exploration feature. */
    public enum DomainStatus {
        /** Already curated into some random set (no need to suggest again). */
        LISTED,
        /** User-suggested and waiting on operator review. */
        PENDING_REVIEW,
        /** Not yet suggested, but meets the criteria to be. */
        ELIGIBLE,
        /** Cannot be suggested (no screenshot, blocked, aliased, unavailable, etc.). */
        INELIGIBLE
    }

    /** Resolve where {@code domainId} sits in the random-exploration lifecycle.
     *  A single query covers all four states; callers don't need to chain checks. */
    public DomainStatus getStatus(int domainId) {
        if (domainId <= 0) return DomainStatus.INELIGIBLE;

        // The eligibility expression mirrors DbBrowseDomainsRandom's filter and adds
        // the screenshot requirement. Membership in the set or queue takes precedence.
        final String sql = """
                SELECT
                    R.DOMAIN_ID IS NOT NULL                    AS in_set,
                    Q.DOMAIN_ID IS NOT NULL                    AS in_queue,
                    (S.DOMAIN_NAME IS NOT NULL
                       AND EC_DOMAIN.STATE < 2
                       AND DAI.SERVER_AVAILABLE
                       AND EC_DOMAIN.DOMAIN_ALIAS IS NULL)     AS eligible
                FROM EC_DOMAIN
                LEFT JOIN EC_RANDOM_DOMAINS R
                    ON R.DOMAIN_ID = EC_DOMAIN.ID
                LEFT JOIN EC_RANDOM_DOMAIN_SUGGESTIONS Q
                    ON Q.DOMAIN_ID = EC_DOMAIN.ID
                LEFT JOIN DATA_DOMAIN_SCREENSHOT S
                    ON S.DOMAIN_NAME = EC_DOMAIN.DOMAIN_NAME
                LEFT JOIN DOMAIN_AVAILABILITY_INFORMATION DAI
                    ON DAI.DOMAIN_ID = EC_DOMAIN.ID
                WHERE EC_DOMAIN.ID = ?
                """;

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql))
        {
            stmt.setInt(1, domainId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) return DomainStatus.INELIGIBLE;
                if (rs.getBoolean("in_set")) return DomainStatus.LISTED;
                if (rs.getBoolean("in_queue")) return DomainStatus.PENDING_REVIEW;
                if (rs.getBoolean("eligible")) return DomainStatus.ELIGIBLE;
                return DomainStatus.INELIGIBLE;
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error resolving random-exploration status for {}", domainId, ex);
            return DomainStatus.INELIGIBLE;
        }
    }

    public SubmitOutcome submitSuggestion(int domainId) {
        // Re-resolve status server-side: the UI state may be stale or forged.
        // INSERT IGNORE on the PK protects against the residual race window.
        DomainStatus status = getStatus(domainId);
        if (status == DomainStatus.LISTED || status == DomainStatus.PENDING_REVIEW) {
            return SubmitOutcome.DUPLICATE;
        }
        if (status != DomainStatus.ELIGIBLE) {
            return SubmitOutcome.INELIGIBLE;
        }

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    INSERT IGNORE INTO EC_RANDOM_DOMAIN_SUGGESTIONS(DOMAIN_ID)
                    VALUES (?)
                    """))
        {
            stmt.setInt(1, domainId);
            int updated = stmt.executeUpdate();
            return updated > 0 ? SubmitOutcome.SUBMITTED : SubmitOutcome.DUPLICATE;
        }
        catch (SQLException ex) {
            logger.warn("SQL error submitting random domain suggestion for {}", domainId, ex);
            return SubmitOutcome.INELIGIBLE;
        }
    }

    public List<SuggestionRow> listSuggestions(int afterId, int numResults) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT EC_RANDOM_DOMAIN_SUGGESTIONS.DOMAIN_ID,
                            EC_DOMAIN.DOMAIN_NAME,
                            EC_RANDOM_DOMAIN_SUGGESTIONS.SUGGESTED_AT
                     FROM EC_RANDOM_DOMAIN_SUGGESTIONS
                     INNER JOIN EC_DOMAIN
                        ON EC_DOMAIN.ID = EC_RANDOM_DOMAIN_SUGGESTIONS.DOMAIN_ID
                     WHERE EC_RANDOM_DOMAIN_SUGGESTIONS.DOMAIN_ID >= ?
                     ORDER BY EC_RANDOM_DOMAIN_SUGGESTIONS.DOMAIN_ID
                     LIMIT ?
                     """))
        {
            stmt.setInt(1, afterId);
            stmt.setInt(2, numResults);
            var rs = stmt.executeQuery();
            List<SuggestionRow> out = new ArrayList<>(numResults);
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp(3);
                out.add(new SuggestionRow(
                        rs.getInt(1),
                        rs.getString(2),
                        ts == null ? "" : SUGGESTED_AT_FORMAT.format(ts.toLocalDateTime())
                ));
            }
            return out;
        }
    }

    /** Approve the given domain ids: insert into the approved-suggestions random set
     *  and remove the queue entries. Performed in a single transaction. */
    public void approveSuggestions(int[] domainIds) throws SQLException {
        if (domainIds.length == 0) return;

        try (var conn = dataSource.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var insert = conn.prepareStatement("""
                     INSERT IGNORE INTO EC_RANDOM_DOMAINS(DOMAIN_ID, DOMAIN_SET)
                     VALUES (?, ?)
                     """);
                 var delete = conn.prepareStatement("""
                     DELETE FROM EC_RANDOM_DOMAIN_SUGGESTIONS
                     WHERE DOMAIN_ID = ?
                     """))
            {
                for (int id : domainIds) {
                    insert.setInt(1, id);
                    insert.setInt(2, RandomDomainSet.APPROVED_SUGGESTIONS.setId);
                    insert.addBatch();
                    delete.setInt(1, id);
                    delete.addBatch();
                }
                insert.executeBatch();
                delete.executeBatch();
                conn.commit();
            }
            catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    public void rejectSuggestions(int[] domainIds) throws SQLException {
        if (domainIds.length == 0) return;

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM EC_RANDOM_DOMAIN_SUGGESTIONS
                     WHERE DOMAIN_ID = ?
                     """))
        {
            for (int id : domainIds) {
                stmt.setInt(1, id);
                stmt.addBatch();
            }
            stmt.executeBatch();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        }
    }

    public record SuggestionRow(int id, String domainName, String suggestedAt) {}
}
