package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.Value;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.api.feeds.RpcFeed;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SearchSiteSubscriptionService {
    private final DbDomainQueries dbDomainQueries;
    private final FeedsClient feedsClient;

    private static final Logger logger = LoggerFactory.getLogger(SearchSiteSubscriptionService.class);

    @Inject
    public SearchSiteSubscriptionService(DbDomainQueries dbDomainQueries, FeedsClient feedsClient) {
        this.dbDomainQueries = dbDomainQueries;
        this.feedsClient = feedsClient;
    }

    public HashSet<Integer> getSubscriptions(Context context) {
        Value cookieValue = context.cookie("sub");
        if (cookieValue.isPresent()) {
            return decodeSubscriptionsCookie(cookieValue.value());
        }
        else {
            return new HashSet<>();
        }
    }

    public void putSubscriptions(Context context, Set<Integer> values) {
        var cookie = new Cookie("sub", encodeSubscriptionsCookie(values));
        cookie.setMaxAge(Duration.ofDays(365));
        context.setResponseCookie(cookie);
    }

    private HashSet<Integer> decodeSubscriptionsCookie(String encodedValue) {
        if (encodedValue == null || encodedValue.isEmpty())
            return new HashSet<>();
        IntBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encodedValue)).asIntBuffer();
        HashSet<Integer> ret = new HashSet<>(buffer.capacity());
        while (buffer.hasRemaining())
            ret.add(buffer.get());
        return ret;
    }

    private String encodeSubscriptionsCookie(Set<Integer> subscriptions) {
        if (subscriptions.isEmpty())
            return "";

        byte[] bytes = new byte[4 * subscriptions.size()];
        IntBuffer buffer = ByteBuffer.wrap(bytes).asIntBuffer();
        for (int val : subscriptions) {
            buffer.put(val);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public boolean isSubscribed(Context context, EdgeDomain domain) {
        try {
            int domainId = dbDomainQueries.getDomainId(domain);

            return getSubscriptions(context).contains(domainId);
        }
        catch (NoSuchElementException ex) {
            return false;
        }
    }

    public void toggleSubscription(Context context, EdgeDomain domain) {

        Set<Integer> subscriptions = getSubscriptions(context);
        int domainId = dbDomainQueries.getDomainId(domain);

        if (subscriptions.contains(domainId)) {
            subscriptions.remove(domainId);
        }
        else {
            subscriptions.add(domainId);
        }

        putSubscriptions(context, subscriptions);
    }

    public Object exportOpml(Context ctx) throws ExecutionException, InterruptedException {
        ctx.setResponseType("text/xml.opml");
        ctx.setResponseHeader("Content-Disposition", "attachment; filename=feeds.opml");

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n");
        sb.append("<opml version=\"2.0\">\n");
        sb.append("<!-- This is an OPM file that can be imported into many feed readers.  See https://opml.org/ for spec. -->\n");
        sb.append("<head>\n");
        sb.append("<title>Marginalia Subscriptions</title>\n");
        sb.append("<dateCreated>").append(LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("</dateCreated>\n");
        sb.append("</head>\n");

        sb.append("<body>\n");
        for (int domainId : getSubscriptions(ctx)) {
            RpcFeed feed = feedsClient.getFeed(domainId).get();
            sb.append("<outline title=\"")
                    .append(feed.getDomain())
                    .append("\" htmlUrl=\"")
                    .append(new EdgeDomain(feed.getDomain()).toRootUrlHttps().toString())
                    .append("\" xmlUrl=\"").append(feed.getFeedUrl()).append("\"/>\n");
        }
        sb.append("</body>\n");
        sb.append("</opml>");

        return sb.toString();
    }
}
