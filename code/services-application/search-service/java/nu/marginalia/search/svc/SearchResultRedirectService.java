package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.StatusCode;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import nu.marginalia.api.searchquery.IndexUrlClient;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.model.NavbarModel;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class SearchResultRedirectService {
    private static final String realIpHeader = System.getProperty("system.realIpHeader", "X-Forwarded-For");
    private static final String salt = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);

    private static final ConcurrentHashMap<Long, Long> lastVisit = new ConcurrentHashMap<>();
    private static final AtomicLong timeCt = new AtomicLong();

    private final IndexUrlClient urlClient;
    private final ScrapeStopper scrapeStopper;

    private static volatile boolean isEnabled = false;

    private static final long SECOND_IN_NANOS = 1_000_000_000L;
    private static final String SEARCH_QUERY_ZONE = "SE";

    @Inject
    public SearchResultRedirectService(IndexUrlClient urlClient,
                                       ScrapeStopper scrapeStopper) {
        this.urlClient = urlClient;
        this.scrapeStopper = scrapeStopper;

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::periodicUpdate, 0, 60, TimeUnit.SECONDS);
    }

    private void periodicUpdate() {

        // Enable during high rate limiter strain, with hysteresis window keeping redirector on until strain drops significantly
        isEnabled = scrapeStopper.isStrained(SEARCH_QUERY_ZONE, isEnabled ? 0.25 : 0.75);

        // Remove stale visit times
        lastVisit.entrySet().removeIf(e -> System.nanoTime() - e.getValue()  > 30*SECOND_IN_NANOS);
    }

    public static boolean isEnabled() {
        return isEnabled;
    }

    /** Remote endpoint for search result redirect URLs,
     *  validates the hash and timing and depending on success,
     *  either redirects immediately to the remote URL
     *  or to an interstitial
     */
    @GET
    @Path("/r/{node}/{docid}/{timestamp}")
    public MapModelAndView redirectToSite(Context context,
                                          @PathParam int node,
                                          @PathParam long docid,
                                          @PathParam String timestamp,
                                          @QueryParam String hash)
            throws TimeoutException
    {
        String ip = resolveIp(context);

        var urlLookup = urlClient.getUrl(node, docid);
        if (urlLookup.isEmpty()) { // Bad URL
            context.setResponseCode(404);
            return null;
        }

        if (validateUrl(hash, ip, timestamp, node, docid)) {
            // Send to URL directly
            context.sendRedirect(StatusCode.TEMPORARY_REDIRECT, urlLookup.get());
            return null;
        }
        else {
            // Send to naughty corner
            context.setResponseHeader("Cache-Control", "no-store");

            return new MapModelAndView("serp/extredirwait.jte",
                    Map.of(
                            "waitDuration", Duration.ofSeconds(5),
                            "navbar", NavbarModel.SEARCH,
                            "redirUrl", urlLookup.get()
                    )
            );
        }
    }

    /** Encode a search result into a URL to the redirector endpoint above.
     *
     * @param context The context of the request
     * @param node The index node that the result came from
     * @param docId The document id
     * */
    public static String createRedirectUrl(AntiscrapeRedirContext context, int node, long docId) {

        StringBuilder endpointBuilder = new StringBuilder();

        endpointBuilder.append("r/")
                .append(Integer.toString(node))
                .append('/')
                .append(Long.toUnsignedString(docId))
                .append('/')
                .append(Long.toUnsignedString(context.timestamp,36));

        String sha256;

        // Make SHA256 of salt + remote IP + endpoint
        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");

            hasher.update(salt.getBytes());
            hasher.update(context.ip.getBytes());
            hasher.update(endpointBuilder.toString().getBytes());

            sha256 = Base64.getUrlEncoder().withoutPadding().encodeToString(hasher.digest());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return endpointBuilder
                .append("?hash=")
                .append(sha256)
                .toString();
    }

    private static boolean validateUrl(String providedSha256, String remoteIp, String timestamp, int node, long docId) {
        StringBuilder endpointBuilder = new StringBuilder();

        endpointBuilder.append("r/")
                .append(Integer.toString(node))
                .append('/')
                .append(Long.toUnsignedString(docId))
                .append('/')
                .append(timestamp);

        String expectedSha256;
        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");

            hasher.update(salt.getBytes());
            hasher.update(remoteIp.getBytes());
            hasher.update(endpointBuilder.toString().getBytes());

            expectedSha256 = Base64.getUrlEncoder().withoutPadding().encodeToString(hasher.digest());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Validate hash
        if (!Objects.equals(providedSha256, expectedSha256)) {
            return false;
        }

        // Stale URL, older than 15 minutes
        long timeDecoded = Long.parseUnsignedLong(timestamp, 36);
        if (System.nanoTime() - timeDecoded > 15*60*SECOND_IN_NANOS) {
            return false;
        }

        // All links on a result page share a timestamp, so this trips when more than one
        // result from the same page is followed within a second.  It's not *impossible* to hit as a human,
        // but a bigger headache when you're a scraper
        Long lastVisitTime = lastVisit.put(timeDecoded, System.nanoTime());
        if (lastVisitTime != null && System.nanoTime() - lastVisitTime < SECOND_IN_NANOS) {
            return false;
        }

        return true;
    }


    @Nullable
    public static AntiscrapeRedirContext createContext(Context ctx) {
        if (!isEnabled()) {
            return null;
        }
        return new AntiscrapeRedirContext(ctx);
    }

    public record AntiscrapeRedirContext(long timestamp, String ip) {
        public AntiscrapeRedirContext(Context ctx) {
            this(acquireTimestamp(), resolveIp(ctx));
        }
    }

    private static long acquireTimestamp() {
        for (;;) {
            long prevTime = timeCt.get();
            long now = System.nanoTime();

            if (prevTime >= now) {
                now = prevTime + 1;
            }
            if (timeCt.compareAndSet(prevTime, now)) {
                return now;
            }
        }
    }

    private static String resolveIp(Context ctx) {
        return ctx.header(realIpHeader).value(ctx.getRemoteAddress());
    }

}

