package nu.marginalia.pq;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Class for querying the mariadb table
 * containing specifications for persistent queries */
public class PersistentQuerySpecsDb {

    private final HikariDataSource ds;

    @Inject
    public PersistentQuerySpecsDb(HikariDataSource ds) {
        this.ds = ds;
    }

    public PersistentQuerySpec create(
            String customerId,
            List<String> termsInclude,
            List<String> termsExclude,
            String languageIsoCode)
    throws SQLException
    {
        try (var conn = ds.getConnection();
             var insertStmt = conn.prepareStatement("""
                INSERT INTO PERSISTENT_QUERIES
                    (PUBLIC_ID, CUSTOMER_ID, TERMS_INCLUDE, TERMS_EXCLUDE, LANG_ISO_CODE, TS_ADDED)
                VALUES
                    (?,?,?,?,?,CURRENT_TIMESTAMP)
                """))
        {
            String publicId = generateKey();

            insertStmt.setString(1, publicId);
            insertStmt.setString(2, customerId);
            insertStmt.setString(3, serializeTerms(termsInclude));
            insertStmt.setString(4, serializeTerms(termsExclude));
            insertStmt.setString(5, languageIsoCode);

            if (1 != insertStmt.executeUpdate()) {
                throw new  SQLException("Insert failed");
            }
            return get(customerId, publicId).orElseThrow(() -> new SQLException("Insert failed"));
        }
    }

    public Optional<PersistentQuerySpec> get(String customerId, String publicId) throws SQLException {
        try (var conn = ds.getConnection();
             var stmt =  conn.prepareStatement("""
                 SELECT * FROM PERSISTENT_QUERIES WHERE PUBLIC_ID=? AND CUSTOMER_ID=?
             """)
            )
        {
            stmt.setString(1, publicId);
            stmt.setString(2, customerId);

            var rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new PersistentQuerySpec(
                        rs.getInt("INTERNAL_ID"),
                        rs.getString("PUBLIC_ID"),
                        rs.getString("CUSTOMER_ID"),
                        deserializeTerms(rs.getString("TERMS_INCLUDE")),
                        deserializeTerms(rs.getString("TERMS_EXCLUDE")),
                        rs.getString("LANG_ISO_CODE"),
                        rs.getObject("TS_ADDED", Instant.class),
                        rs.getObject("TS_DISABLED", Instant.class)

                ));
            }
        }

        return Optional.empty();
    }

    public boolean disable(PersistentQuerySpec persistentQuerySpec) throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement("""
                     UPDATE  PERSISTENT_QUERIES
                     SET TS_DISABLED = CURRENT_TIMESTAMP
                     WHERE PUBLIC_ID=? AND CUSTOMER_ID=?
                     """)
        ) {
            stmt.setString(1, persistentQuerySpec.publicId());
            stmt.setString(2, persistentQuerySpec.customerId());

            return 1 == stmt.executeUpdate();
        }
    }

    public List<PersistentQuerySpec> getEnabledQueries() throws SQLException {
        List<PersistentQuerySpec> ret = new ArrayList<>();

        try (var conn = ds.getConnection();
             var stmt =  conn.prepareStatement("""
                 SELECT * FROM PERSISTENT_QUERIES WHERE TS_DISABLED IS NULL
             """)
        )
        {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(new PersistentQuerySpec(
                        rs.getInt("INTERNAL_ID"),
                        rs.getString("PUBLIC_ID"),
                        rs.getString("CUSTOMER_ID"),
                        deserializeTerms(rs.getString("TERMS_INCLUDE")),
                        deserializeTerms(rs.getString("TERMS_EXCLUDE")),
                        rs.getString("LANG_ISO_CODE"),
                        rs.getObject("TS_ADDED", Instant.class),
                        rs.getObject("TS_DISABLED", Instant.class)
                ));
            }
        }

        return ret;
    }


    public List<PersistentQuerySpec> getQueriesForCustomer(String customerId) throws SQLException {
        List<PersistentQuerySpec> ret = new ArrayList<>();

        try (var conn = ds.getConnection();
             var stmt =  conn.prepareStatement("""
                 SELECT * FROM PERSISTENT_QUERIES WHERE CUSTOMER_ID = ?
             """)
        ) {
            stmt.setString(1, customerId);

            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(new PersistentQuerySpec(
                        rs.getInt("INTERNAL_ID"),
                        rs.getString("PUBLIC_ID"),
                        rs.getString("CUSTOMER_ID"),
                        deserializeTerms(rs.getString("TERMS_INCLUDE")),
                        deserializeTerms(rs.getString("TERMS_EXCLUDE")),
                        rs.getString("LANG_ISO_CODE"),
                        rs.getObject("TS_ADDED", Instant.class),
                        rs.getObject("TS_DISABLED", Instant.class)
                ));
            }
        }

        return ret;
    }

    private long lastTs = Long.MIN_VALUE;
    private long counter = 0;
    private final Random random = new Random(System.nanoTime());

    private synchronized String generateKey() {
        StringBuilder keyBuilder = new StringBuilder();

        long currentTs = System.currentTimeMillis();

        if (lastTs == currentTs) counter++;
        else counter = 0;

        lastTs = currentTs;

        // If you squint a lot this is basically an UUID with better uniqueness guarantees,
        // except we put the timestamp first to be nicer to the db queries
        keyBuilder.append(Long.toUnsignedString(currentTs, 36));
        keyBuilder.append('-');
        keyBuilder.append(counter);
        keyBuilder.append('-');
        keyBuilder.append(Long.toUnsignedString(random.nextLong(), 36));
        keyBuilder.append(Long.toUnsignedString(random.nextLong(), 36));
        keyBuilder.append(Long.toUnsignedString(random.nextLong(), 36));
        keyBuilder.append(Long.toUnsignedString(random.nextLong(), 36));

        return keyBuilder.toString();
    }

    private String serializeTerms(List<String> terms) {
        return Strings.join(terms, ' ');
    }

    private List<String> deserializeTerms(String terms) {
        return List.of(StringUtils.split(terms, ' '));
    }

}
