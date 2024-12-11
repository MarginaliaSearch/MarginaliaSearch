package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.MapModelAndView;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.model.NavbarModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders the front page (index) */
@Singleton
public class SearchFrontPageService {

    private final HikariDataSource dataSource;
    private final SearchQueryCountService searchVisitorCount;
    private final WebsiteUrl websiteUrl;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchFrontPageService(HikariDataSource dataSource,
                                  SearchQueryCountService searchVisitorCount,
                                  WebsiteUrl websiteUrl
    ) {
        this.dataSource = dataSource;
        this.searchVisitorCount = searchVisitorCount;
        this.websiteUrl = websiteUrl;
    }

    @GET
    @Path("/")
    public MapModelAndView render() {
        return new MapModelAndView("serp/start.jte")
                .put("navbar", NavbarModel.SEARCH)
                .put("websiteUrl", websiteUrl);
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

    /* FIXME
    public Object renderNewsFeed(Request request, Response response) {
        List<NewsItem> newsItems = getNewsItems();

        StringBuilder sb = new StringBuilder();

        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                <channel>
                <title>Marginalia Search News and Mentions</title>
                <link>https://search.marginalia.nu/</link>
                <description>News and Mentions of Marginalia Search</description>
                <language>en-us</language>
                <ttl>60</ttl>
                """);

        sb.append("<lastBuildDate>").append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("</lastBuildDate>\n");
        sb.append("<pubDate>").append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("</pubDate>\n");
        sb.append("<ttl>60</ttl>\n");
        for (var item : newsItems) {
            sb.append("<item>\n");
            sb.append("<title>").append(item.title()).append("</title>\n");
            sb.append("<link>").append(item.url()).append("</link>\n");
            if (item.source != null) {
                sb.append("<author>").append(item.source()).append("</author>\n");
            }
            sb.append("<pubDate>").append(item.date().atStartOfDay().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("</pubDate>\n");
            sb.append("</item>\n");
        }
        sb.append("</channel>\n");
        sb.append("</rss>\n");

        response.type("application/rss+xml");

        return sb.toString();
    }*/

    private record IndexModel(List<NewsItem> news, int searchPerMinute) { }
    private record NewsItem(String title, String url, String source, LocalDate date) {}
}
