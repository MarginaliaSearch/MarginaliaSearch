package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.svc.SearchQueryCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders the front page (index) */
@Singleton
public class SearchFrontPageService {

    private final MustacheRenderer<IndexModel> template;
    private final HikariDataSource dataSource;
    private final SearchQueryCountService searchVisitorCount;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchFrontPageService(RendererFactory rendererFactory,
                                  HikariDataSource dataSource,
                                  SearchQueryCountService searchVisitorCount
                        ) throws IOException {
        this.template = rendererFactory.renderer("search/index/index");
        this.dataSource = dataSource;
        this.searchVisitorCount = searchVisitorCount;
    }

    public String render(Request request, Response response) {
        response.header("Cache-control", "public,max-age=3600");

        return template.render(new IndexModel(
                getNewsItems(),
                searchVisitorCount.getQueriesPerMinute()
        ));
    }


    private List<NewsItem> getNewsItems() {
        List<NewsItem> items = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT TITLE, LINK, SOURCE, LIST_DATE FROM SEARCH_NEWS_FEED ORDER BY LIST_DATE DESC
                """)) {

            var rep = stmt.executeQuery();

            while (rep.next()) {
                items.add(new NewsItem(
                        rep.getString(1),
                        rep.getString(2),
                        rep.getString(3),
                        rep.getDate(4).toLocalDate()));
            }
        }
        catch (SQLException ex) {
            logger.warn("Failed to fetch news items", ex);
        }

        return items;
    }

    private record IndexModel(List<NewsItem> news, int searchPerMinute) { }
    private record NewsItem(String title, String url, String source, LocalDate date) {}
}
