package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.api.feeds.RpcFeed;
import nu.marginalia.search.model.NavbarModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Renders the front page (index) */
@Singleton
public class SearchFrontPageService {

    private final SearchQueryCountService searchVisitorCount;
    private final FeedsClient feedsClient;
    private final WebsiteUrl websiteUrl;
    private final SearchSiteSubscriptionService  subscriptionService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchFrontPageService(SearchQueryCountService searchVisitorCount,
                                  FeedsClient feedsClient,
                                  WebsiteUrl websiteUrl,
                                  SearchSiteSubscriptionService subscriptionService
    ) {
        this.searchVisitorCount = searchVisitorCount;
        this.feedsClient = feedsClient;
        this.websiteUrl = websiteUrl;
        this.subscriptionService = subscriptionService;
    }

    @GET
    @Path("/")
    public MapModelAndView render(Context context) {

        List<NewsItem> newsItems = getNewsItems(context);

        IndexModel model = new IndexModel(newsItems, searchVisitorCount.getQueriesPerMinute());

        return new MapModelAndView("serp/start.jte")
                .put("navbar", NavbarModel.SEARCH)
                .put("model", model)
                .put("websiteUrl", websiteUrl);
    }

    private List<NewsItem> getNewsItems(Context context) {

        Set<Integer> subscriptions = subscriptionService.getSubscriptions(context);

        if (subscriptions.isEmpty())
            return List.of();

        List<CompletableFuture<RpcFeed>> feedResults = new ArrayList<>();

        for (int sub : subscriptions) {
            feedResults.add(feedsClient.getFeed(sub));
        }

        List<NewsItem> ret = new ArrayList<>();
        for (var result : feedResults) {
            try {
                RpcFeed feed = result.get();

                for (var item : feed.getItemsList()) {
                    String title = item.getTitle();
                    if (title.isBlank()) {
                        title = "[Missing Title]";
                    }
                    ret.add(new NewsItem(title, item.getUrl(), feed.getDomain(), item.getDescription(), item.getDate()));
                }
            }
            catch (Exception ex) {
                logger.error("Failed to fetch news items", ex);
            }
        }

        ret.sort(Comparator.comparing(NewsItem::date).reversed());
        if (ret.size() > 25) {
            ret.subList(25, ret.size()).clear();
        }
        return ret;
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

    public record IndexModel(List<NewsItem> news, int searchPerMinute) { }
    public record NewsItem(String title, String url, String domain, String description, String date) {}
}
