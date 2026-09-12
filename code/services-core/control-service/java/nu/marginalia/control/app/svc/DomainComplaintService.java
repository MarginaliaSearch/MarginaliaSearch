package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.app.model.DomainComplaintCategory;
import nu.marginalia.control.app.model.DomainComplaintModel;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;


/** Service for handling domain complaints. This code has an user-facing correspondent in
 * SearchFlagSiteService in search-service
 */
public class DomainComplaintService {
    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final ControlBlacklistService blacklistService;
    private final RandomExplorationService randomExplorationService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainComplaintService(HikariDataSource dataSource,
                                  ControlRendererFactory rendererFactory,
                                  ControlBlacklistService blacklistService,
                                  RandomExplorationService randomExplorationService
    ) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
        this.blacklistService = blacklistService;
        this.randomExplorationService = randomExplorationService;
    }

    public void register(Jooby jooby) throws IOException {
        var domainComplaintsRenderer = rendererFactory.renderer("control/app/domain-complaints");

        jooby.get("/complaints", ctx -> domainComplaintsRenderer.render(complaintsModel(ctx)));
        jooby.post("/complaints/{domain}", ctx -> Redirects.redirectToComplaints.render(reviewComplaint(ctx)));
    }

    private Object complaintsModel(Context ctx) {
        Map<Boolean, List<DomainComplaintModel>> complaintsByReviewed =
                getComplaints().stream().collect(Collectors.partitioningBy(DomainComplaintModel::reviewed));

        var reviewed = complaintsByReviewed.get(true);
        var unreviewed = complaintsByReviewed.get(false);

        reviewed.sort(Comparator.comparing(DomainComplaintModel::reviewDate).reversed());
        unreviewed.sort(Comparator.comparing(DomainComplaintModel::fileDate).reversed());

        return Map.of("complaintsNew", unreviewed, "complaintsReviewed", reviewed);
    }

    private Object reviewComplaint(Context ctx) {
        var domain = new EdgeDomain(ctx.path("domain").value());
        String action = ctx.lookup("action", QUERY, FORM).valueOrNull();

        logger.info("Reviewing complaint for domain {} with action {}", domain, action);

        try {
            switch (action) {
                case "noop" -> reviewNoAction(domain);
                case "appeal" -> approveAppealBlacklisting(domain);
                case "no-random" -> removeFromRandomDomains(domain);
                case "blacklist" -> blacklistDomain(domain);
                default -> throw new UnsupportedOperationException();
            }

            return "";
        }
        catch (Exception ex) {
            logger.error("Error reviewing complaint for domain " + domain, ex);
            throw new io.jooby.exception.StatusCodeException(io.jooby.StatusCode.SERVER_ERROR);
        }
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

    private void removeFromRandomDomains(EdgeDomain domain) throws SQLException {
        randomExplorationService.removeDomain(domain);

        setDecision(domain, "REMOVED-FROM-RANDOM");
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
