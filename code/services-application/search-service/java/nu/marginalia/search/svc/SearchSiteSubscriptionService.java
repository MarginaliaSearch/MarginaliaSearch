package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.Value;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

public class SearchSiteSubscriptionService {
    private final DbDomainQueries dbDomainQueries;

    private static final Logger logger = LoggerFactory.getLogger(SearchSiteSubscriptionService.class);

    @Inject
    public SearchSiteSubscriptionService(DbDomainQueries dbDomainQueries) {
        this.dbDomainQueries = dbDomainQueries;
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
}
