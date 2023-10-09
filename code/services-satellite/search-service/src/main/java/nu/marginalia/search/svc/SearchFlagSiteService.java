package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Service for handling flagging sites. This code has an admin-facing correspondent in
 * DomainComplaintService in control-service
 */
public class SearchFlagSiteService {
    private final MustacheRenderer<FlagSiteViewModel> formTemplate;
    private final HikariDataSource dataSource;

    private final CategoryItem unknownCategory = new CategoryItem("unknown", "Unknown");

    private final List<CategoryItem> categories =
            List.of(
                    new CategoryItem("spam", "Spam"),
                    new CategoryItem("freebooting", "Reposting Stolen Content"),
                    new CategoryItem("broken", "Broken Website"),
                    new CategoryItem("shock", "Shocking/Offensive"),
                    new CategoryItem("blacklist", "Review Blacklisting")
            );

    private final Map<String, CategoryItem> categoryItemMap =
            categories.stream().collect(Collectors.toMap(CategoryItem::categoryName, Function.identity()));
    @Inject
    public SearchFlagSiteService(RendererFactory rendererFactory,
                                 HikariDataSource dataSource) throws IOException {
        formTemplate = rendererFactory.renderer("search/indict/indict-form");
        this.dataSource = dataSource;
    }

    public Object flagSiteForm(Request request, Response response) throws SQLException {
        final int domainId = Integer.parseInt(request.params("domainId"));

        var model = getModel(domainId, false);
        return formTemplate.render(model);
    }

    public Object flagSiteAction(Request request, Response response) throws SQLException {

        int domainId = Integer.parseInt(request.params("domainId"));

        var formData = new FlagSiteFormData(
                domainId,
                request.queryParams("category"),
                request.queryParams("description"),
                request.queryParams("samplequery")
        );

        insertComplaint(formData);

        return formTemplate.render(getModel(domainId, true));
    }

    private void insertComplaint(FlagSiteFormData formData) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     """
                        INSERT INTO DOMAIN_COMPLAINT(DOMAIN_ID, CATEGORY, DESCRIPTION, SAMPLE) VALUES (?, ?, ?, ?)
                        """)) {
            stmt.setInt(1, formData.domainId);
            stmt.setString(2, formData.category);
            stmt.setString(3, formData.description);
            stmt.setString(4, formData.sampleQuery);
            stmt.executeUpdate();
        }
    }

    private FlagSiteViewModel getModel(int id, boolean isSubmitted) throws SQLException {


        try (var conn = dataSource.getConnection();
             var complaintsStmt = conn.prepareStatement("""
                     SELECT CATEGORY, FILE_DATE, REVIEWED, DECISION
                     FROM DOMAIN_COMPLAINT
                     WHERE DOMAIN_ID=?
                     """);
             var stmt = conn.prepareStatement(
                     """
                        SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE EC_DOMAIN.ID=?
                        """))
        {
            List<FlagSiteComplaintModel> complaints = new ArrayList<>();

            complaintsStmt.setInt(1, id);
            ResultSet rs = complaintsStmt.executeQuery();

            while (rs.next()) {
                complaints.add(new FlagSiteComplaintModel(
                        categoryItemMap.getOrDefault(rs.getString(1), unknownCategory).categoryDesc,
                        rs.getString(2),
                        rs.getBoolean(3),
                        rs.getString(4)));
            }

            stmt.setInt(1, id);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                Spark.halt(404);
            }
            return new FlagSiteViewModel(id,
                    rs.getString(1),
                    categories,
                    complaints,
                    isSubmitted);
        }
    }

    public record CategoryItem(String categoryName, String categoryDesc) {}
    public record FlagSiteViewModel(int domainId, String domain, List<CategoryItem> category, List<FlagSiteComplaintModel> complaints, boolean isSubmitted) {}
    public record FlagSiteComplaintModel(String category, String submitTime, boolean isReviewed, String decision) {}
    public record FlagSiteFormData(int domainId, String category, String description, String sampleQuery) {};
}
