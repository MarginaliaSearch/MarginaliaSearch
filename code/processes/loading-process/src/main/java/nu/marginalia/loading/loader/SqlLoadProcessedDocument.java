package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static java.sql.Statement.SUCCESS_NO_INFO;

public class SqlLoadProcessedDocument {
    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(SqlLoadProcessedDocument.class);

    @Inject
    public SqlLoadProcessedDocument(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DROP PROCEDURE IF EXISTS INSERT_PAGE_VISIT");
                stmt.execute("DROP PROCEDURE IF EXISTS INSERT_PAGE_VISIT_BAD");
                stmt.execute("""
                       CREATE PROCEDURE INSERT_PAGE_VISIT (
                               IN URL_ID INT,
                               IN STATE VARCHAR(32),
                               IN TITLE VARCHAR(255),
                               IN DESCRIPTION VARCHAR(255),
                               IN LENGTH INT,
                               IN FEATURES INT,
                               IN STANDARD VARCHAR(32),
                               IN QUALITY DOUBLE,
                               IN HASH BIGINT,
                               IN PUB_YEAR SMALLINT)
                       BEGIN
                               SET FOREIGN_KEY_CHECKS=0;
                               REPLACE INTO EC_PAGE_DATA(ID, TITLE, DESCRIPTION, WORDS_TOTAL, FORMAT, FEATURES, DATA_HASH, QUALITY, PUB_YEAR) VALUES (URL_ID, TITLE, DESCRIPTION, LENGTH, STANDARD, FEATURES, HASH, QUALITY, PUB_YEAR);
                               UPDATE EC_URL SET VISITED=1, STATE=STATE WHERE ID=URL_ID;
                               SET FOREIGN_KEY_CHECKS=1;
                       END
                        """);
                stmt.execute("""
                       CREATE PROCEDURE INSERT_PAGE_VISIT_BAD (
                               IN URL_ID INT,
                               IN STATE VARCHAR(32))
                       BEGIN
                               UPDATE EC_URL SET VISITED=1, STATE=STATE WHERE ID=URL_ID;
                               DELETE FROM EC_PAGE_DATA WHERE ID=URL_ID;
                       END
                        """);

            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Failed to set up loader", ex);
        }
    }

    public void load(LoaderData data, List<LoadProcessedDocument> documents) {

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareCall("CALL INSERT_PAGE_VISIT(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            conn.setAutoCommit(false);

            int cnt = 0; int batchOffset = 0;
            for (var doc : documents) {
                int urlId = data.getUrlId(doc.url());
                if (urlId <= 0) {
                    logger.warn("Failed to resolve ID for URL {}", doc.url());
                    return;
                }

                stmt.setInt(1, urlId);
                stmt.setString(2, doc.state().name());
                stmt.setString(3, doc.title());
                stmt.setString(4, StringUtils.truncate(doc.description(), 255));
                stmt.setInt(5, doc.length());
                stmt.setInt(6, doc.htmlFeatures());
                stmt.setString(7, doc.standard());
                stmt.setDouble(8, doc.quality());
                stmt.setLong(9, doc.hash());
                if (doc.pubYear() != null) {
                    stmt.setShort(10, (short) doc.pubYear().intValue());
                }
                else {
                    stmt.setInt(10, Types.SMALLINT);
                }
                stmt.addBatch();

                if (++cnt == 100) {
                    var ret = stmt.executeBatch();
                    conn.commit();

                    for (int rv = 0; rv < cnt; rv++) {
                        if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                            logger.warn("load({}) -- bad row count {}", documents.get(batchOffset + rv), ret[rv]);
                        }
                    }

                    cnt = 0;
                    batchOffset += 100;
                }
            }
            if (cnt > 0) {
                var ret = stmt.executeBatch();
                conn.commit();
                for (int rv = 0; rv < cnt; rv++) {
                    if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                        logger.warn("load({}) -- bad row count {}", documents.get(batchOffset + rv), ret[rv]);
                    }
                }
            }

            conn.setAutoCommit(true);

        } catch (SQLException ex) {
            logger.warn("SQL error inserting document", ex);

            if (getClass().desiredAssertionStatus())
                throw new RuntimeException(ex);
        }
    }

    public void loadWithError(LoaderData data, List<LoadProcessedDocumentWithError> documents) {

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareCall("CALL INSERT_PAGE_VISIT_BAD(?, ?)")) {

            conn.setAutoCommit(false);

            int cnt = 0; int batchOffset = 0;
            for (var doc : documents) {
                int urlId = data.getUrlId(doc.url());
                if (urlId < 0) {
                    logger.warn("Failed to resolve ID for URL {}", doc.url());
                    return;
                }

                stmt.setInt(1, urlId);
                stmt.setString(2, doc.state().name());
                stmt.addBatch();

                if (++cnt == 100) {
                    var ret = stmt.executeBatch();
                    conn.commit();

                    for (int rv = 0; rv < cnt; rv++) {
                        if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                            logger.warn("load({}) -- bad row count {}", documents.get(batchOffset + rv), ret[rv]);
                        }
                    }

                    cnt = 0;
                    batchOffset += 100;
                }
            }
            if (cnt > 0) {
                var ret = stmt.executeBatch();
                conn.commit();
                for (int rv = 0; rv < cnt; rv++) {
                    if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                        logger.warn("load({}) -- bad row count {}", documents.get(batchOffset + rv), ret[rv]);
                    }
                }
            }

            conn.setAutoCommit(true);
        } catch (SQLException ex) {
            logger.warn("SQL error inserting failed document", ex);

            if (getClass().desiredAssertionStatus())
                throw new RuntimeException(ex);
        }

    }
}
