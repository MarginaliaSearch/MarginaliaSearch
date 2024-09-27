package nu.marginalia.livecapture;

import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class ScreenshotDbOperations {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotDbOperations.class);

    public synchronized static void flagDomainAsFetched(Connection conn, EdgeDomain domain) {
        try (var stmt = conn.prepareStatement("""
                REPLACE INTO DATA_DOMAIN_HISTORY(DOMAIN_NAME, SCREENSHOT_DATE) 
                VALUES (?, NOW())
                """))
        {
            stmt.setString(1, domain.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to flag domain as fetched", e);
        }
    }

    public synchronized static void uploadScreenshot(Connection conn, EdgeDomain domain, byte[] pngBytes) {
        try (var stmt = conn.prepareStatement("""
                REPLACE INTO DATA_DOMAIN_SCREENSHOT(DOMAIN_NAME, CONTENT_TYPE, DATA) 
                VALUES (?,?,?)
                """);
             var is = new ByteArrayInputStream(pngBytes)
        ) {
            stmt.setString(1, domain.toString());
            stmt.setString(2, "image/png");
            stmt.setBlob(3, is);
            stmt.executeUpdate();
        } catch (SQLException | IOException e) {
            logger.error("Failed to upload screenshot", e);
        }

        flagDomainAsFetched(conn, domain);
    }

    public static boolean isEligibleForScreengrab(Connection conn, int domainId) {
        if (domainId <= 0) // Invalid domain ID
            return false;

        try (var stmt = conn.prepareStatement("""
                SELECT 1 FROM DATA_DOMAIN_HISTORY
                INNER JOIN WMSA_prod.EC_DOMAIN ON DATA_DOMAIN_HISTORY.DOMAIN_NAME = EC_DOMAIN.DOMAIN_NAME
                WHERE EC_DOMAIN.ID = ?
                    AND SCREENSHOT_DATE > DATE_SUB(NOW(), INTERVAL 1 MONTH)
                """))
        {
            stmt.setInt(1, domainId);

            try (var rs = stmt.executeQuery()) {
                return !rs.next();
            }
        } catch (SQLException e) {
            logger.error("Failed to check eligibility for screengrab", e);
            return false;
        }
    }

    public static Optional<EdgeDomain> getDomainName(Connection conn, int domainId) {
        try (var stmt = conn.prepareStatement("""
                SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE ID = ?
                """))
        {
            stmt.setInt(1, domainId);

            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString(1)).map(EdgeDomain::new);
                }
            }
        }
        catch (SQLException ex) {
            logger.error("Failed to get domain name", ex);
        }
        return Optional.empty();
    }
}
