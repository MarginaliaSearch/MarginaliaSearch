package nu.marginalia.wmsa.edge.converting.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocumentWithError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
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
                               IN HASH INT)
                       BEGIN
                               SET FOREIGN_KEY_CHECKS=0;
                               REPLACE INTO EC_PAGE_DATA(ID, TITLE, DESCRIPTION, WORDS_TOTAL, FORMAT, FEATURES, DATA_HASH, QUALITY) VALUES (URL_ID, TITLE, DESCRIPTION, LENGTH, STANDARD, FEATURES, HASH, QUALITY);
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
                               DELETE FROM PAGE_DATA WHERE ID=URL_ID;
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
             var stmt = conn.prepareCall("CALL INSERT_PAGE_VISIT(?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            conn.setAutoCommit(false);

            for (var doc : documents) {
                int urlId = data.getUrlId(doc.url());
                if (urlId < 0) {
                    logger.warn("Failed to resolve ID for URL {}", doc.url());
                    return;
                }

                stmt.setInt(1, urlId);
                stmt.setString(2, doc.state().name());
                stmt.setString(3, doc.title());
                stmt.setString(4, doc.description());
                stmt.setInt(5, doc.length());
                stmt.setInt(6, doc.htmlFeatures());
                stmt.setString(7, doc.standard().name());
                stmt.setDouble(8, doc.quality());
                stmt.setInt(9, (int) doc.hash());
                stmt.addBatch();
            }
            var ret = stmt.executeBatch();

            for (int rv = 0; rv < documents.size(); rv++) {
                if (ret[rv] < 1 && ret[rv] != SUCCESS_NO_INFO) {
                    logger.warn("load({}) -- bad row count {}", documents.get(rv), ret[rv]);
                }
            }

            conn.commit();
        } catch (SQLException ex) {
            logger.warn("SQL error inserting document", ex);
        }


    }

    public void loadWithError(LoaderData data, List<LoadProcessedDocumentWithError> documents) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareCall("CALL INSERT_PAGE_VISIT_BAD(?, ?)")) {

            for (var doc : documents) {
                int urlId = data.getUrlId(doc.url());
                if (urlId < 0) {
                    logger.warn("Failed to resolve ID for URL {}", doc.url());
                    return;
                }

                stmt.setInt(1, urlId);
                stmt.setString(2, doc.state().name());
                stmt.addBatch();
            }
            var ret = stmt.executeBatch();
            for (int rv = 0; rv < documents.size(); rv++) {
                if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                    logger.warn("load({}) -- bad row count {}", documents.get(rv), ret[rv]);
                }
            }
        } catch (SQLException ex) {
            logger.warn("SQL error inserting failed document", ex);
        }

    }
}
