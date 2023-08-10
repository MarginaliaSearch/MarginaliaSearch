package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.model.DomainComplaintCategory;
import nu.marginalia.control.model.DomainComplaintModel;
import nu.marginalia.model.EdgeDomain;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/** Service for handling domain complaints. This code has an user-facing correspondent in
 * SearchFlagSiteService in search-service
 */
public class DomainComplaintService {
    private final HikariDataSource dataSource;
    private final ControlBlacklistService blacklistService;

    @Inject
    public DomainComplaintService(HikariDataSource dataSource,
                                  ControlBlacklistService blacklistService
    ) {
        this.dataSource = dataSource;
        this.blacklistService = blacklistService;
    }

    public List<DomainComplaintModel> getComplaints() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT EC_DOMAIN.DOMAIN_NAME AS DOMAIN, CATEGORY, DESCRIPTION, SAMPLE, FILE_DATE, REVIEWED, DECISION, REVIEW_DATE
                     FROM DOMAIN_COMPLAINT LEFT JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_COMPLAINT.DOMAIN_ID
                     """)) {
            List<DomainComplaintModel> complaints = new ArrayList<>();
            var rs = stmt.executeQuery();
            while (rs.next()) {
                complaints.add(new DomainComplaintModel(
                        rs.getString("DOMAIN"),
                        DomainComplaintCategory.fromCategoryName(rs.getString("CATEGORY")),
                        rs.getString("DESCRIPTION"),
                        rs.getString("SAMPLE"),
                        rs.getString("DECISION"),
                        rs.getTimestamp("FILE_DATE").toLocalDateTime().toString(),
                        Optional.ofNullable(rs.getTimestamp("REVIEW_DATE"))
                                .map(Timestamp::toLocalDateTime).map(Object::toString).orElse(null),
                        rs.getBoolean("REVIEWED")
                ));
            }
            return complaints;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void approveAppealBlacklisting(EdgeDomain domain) {
        blacklistService.removeFromBlacklist(domain);
        setDecision(domain, "APPROVED");
    }

    public void blacklistDomain(EdgeDomain domain) {
        blacklistService.addToBlacklist(domain, "Domain complaint");

        setDecision(domain, "BLACKLISTED");
    }

    public void reviewNoAction(EdgeDomain domain) {
        setDecision(domain, "REJECTED");
    }



    private void setDecision(EdgeDomain domain, String decision) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     UPDATE DOMAIN_COMPLAINT SET DECISION=?, REVIEW_DATE=NOW()
                     WHERE DOMAIN_ID=(SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?)
                     AND DECISION IS NULL
                     """)) {
            stmt.setString(1, decision);
            stmt.setString(2, domain.toString());
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
