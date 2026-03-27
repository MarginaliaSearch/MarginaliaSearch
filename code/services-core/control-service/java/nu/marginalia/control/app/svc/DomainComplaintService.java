package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.exception.StatusCodeException;
import io.jooby.StatusCode;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.app.model.DomainComplaintCategory;
import nu.marginalia.control.app.model.DomainComplaintModel;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    private ControlRendererFactory.Renderer domainComplaintsRenderer;

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
        domainComplaintsRenderer = rendererFactory.renderer("control/app/domain-complaints");

        jooby.get("/complaints", this::complaintsModel);
        jooby.post("/complaints/{domain}", this::reviewComplaint);
    }

    private Object complaintsModel(Context ctx) {
        Map<Boolean, List<DomainComplaintModel>> complaintsByReviewed =
                getComplaints().stream().collect(Collectors.partitioningBy(DomainComplaintModel::reviewed));

        List<DomainComplaintModel> reviewed = complaintsByReviewed.get(true);
        List<DomainComplaintModel> unreviewed = complaintsByReviewed.get(false);

        reviewed.sort(Comparator.comparing(DomainComplaintModel::reviewDate).reversed());
        unreviewed.sort(Comparator.comparing(DomainComplaintModel::fileDate).reversed());

        ctx.setResponseType(MediaType.html);
        return domainComplaintsRenderer.render(
                Map.of("complaintsNew", unreviewed, "complaintsReviewed", reviewed));
    }

    private Object reviewComplaint(Context ctx) {
        EdgeDomain domain = new EdgeDomain(ctx.path("domain").value());
        String action = ctx.query("action").valueOrNull();

        logger.info("Reviewing complaint for domain {} with action {}", domain, action);

        try {
            switch (action) {
                case "noop" -> reviewNoAction(domain);
                case "appeal" -> approveAppealBlacklisting(domain);
                case "no-random" -> removeFromRandomDomains(domain);
                case "blacklist" -> blacklistDomain(domain);
                default -> throw new UnsupportedOperationException();
            }
        }
        catch (Exception ex) {
            logger.error("Error reviewing complaint for domain " + domain, ex);
            throw new StatusCodeException(StatusCode.SERVER_ERROR);
        }

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToComplaints.render(null);
    }

    public List<DomainComplaintModel> getComplaints() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT EC_DOMAIN.DOMAIN_NAME AS DOMAIN, CATEGORY, DESCRIPTION, SAMPLE, FILE_DATE, REVIEWED, DECISION, REVIEW_DATE
                     FROM DOMAIN_COMPLAINT LEFT JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_COMPLAINT.DOMAIN_ID
                     """)) {
            List<DomainComplaintModel> complaints = new ArrayList<>();
            ResultSet rs = stmt.executeQuery();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
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
