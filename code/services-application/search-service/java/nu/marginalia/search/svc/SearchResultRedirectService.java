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

    @Inject
    public SearchResultRedirectService(IndexUrlClient urlClient,
                                       ScrapeStopper scrapeStopper) {
        this.urlClient = urlClient;
        this.scrapeStopper = scrapeStopper;

        Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("result-redirect-maintenance").daemon().factory()
        ).scheduleAtFixedRate(
                () -> {
                    isEnabled = scrapeStopper.isStrained();
                    lastVisit.entrySet().removeIf(e -> System.nanoTime() - e.getValue()  > 30_000_000_000L);
                },
                0,
                60,
                TimeUnit.SECONDS
        );
    }

    public static boolean isEnabled() {
        return isEnabled;
    }

    @Nullable
    public static AntiscrapeRedirContext createContext(Context ctx) {
        if (!isEnabled()) {
            return null;
        }
        return new AntiscrapeRedirContext(ctx);
    }

    public record AntiscrapeRedirContext(long ts, String ip) {
        public AntiscrapeRedirContext(Context ctx) {
            this(acquireTimestamp(), resolveIp(ctx));
        }
    }

    private static String resolveIp(Context ctx) {
        return ctx.header(realIpHeader).value(ctx.getRemoteAddress());
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


    @GET
    @Path("/r/{node}/{docid}/{ts}")
    public MapModelAndView redirectToSite(Context context,
                                          @PathParam int node,
                                          @PathParam long docid,
                                          @PathParam String ts,
                                          @QueryParam String hash)
            throws TimeoutException
    {
        String ip = resolveIp(context);

        var urlLookup = urlClient.getUrl(node, docid);
        if (urlLookup.isEmpty()) {
            context.setResponseCode(404);
            return null;
        }

        if (validateUrl(hash, ip, ts, node, docid)) {
            context.sendRedirect(StatusCode.TEMPORARY_REDIRECT, urlLookup.get());
            return null;
        }

        context.setResponseHeader("Cache-Control", "no-store");

        return new MapModelAndView("serp/extredirwait.jte",
                Map.of(
                        "waitDuration", Duration.ofSeconds(5),
                        "navbar", NavbarModel.SEARCH,
                        "redirUrl", urlLookup.get()
                )
        );
    }

    public static String encode(AntiscrapeRedirContext context, int node, long docId) {

        StringBuilder sb = new StringBuilder();

        sb.append("r/")
                .append(Integer.toString(node))
                .append('/')
                .append(Long.toUnsignedString(docId))
                .append('/')
                .append(Long.toUnsignedString(context.ts,36));

        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");
            hasher.update(salt.getBytes());
            hasher.update(context.ip.getBytes());
            hasher.update(sb.toString().getBytes());

            return sb
                    .append("?hash=")
                    .append(Base64.getUrlEncoder().withoutPadding().encodeToString(hasher.digest()))
                    .toString();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean validateUrl(String providedHash, String remoteIp, String time, int node, long docId) {
        StringBuilder sb = new StringBuilder();

        sb.append("r/")
                .append(Integer.toString(node))
                .append('/')
                .append(Long.toUnsignedString(docId))
                .append('/')
                .append(time);

        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");

            hasher.update(salt.getBytes());
            hasher.update(remoteIp.getBytes());
            hasher.update(sb.toString().getBytes());

            if (!Objects.equals(providedHash, Base64.getUrlEncoder().withoutPadding().encodeToString(hasher.digest()))) {
                return false;
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Stale URL, older than 15 minutes
        long timeDecoded = Long.parseUnsignedLong(time, 36);
        if (System.nanoTime() - timeDecoded > 15*60*1000_000_000L) {
            return false;
        }

        // All links on a result page share a timestamp, so this trips when more than one
        // result from the same page is followed within a second.  It's not *impossible* to hit as a human,
        // but a bigger headache when you're a scraper
        Long lastVisitTime = lastVisit.put(timeDecoded, System.nanoTime());
        if (lastVisitTime != null && System.nanoTime() - lastVisitTime < 1000_000_000L) {
            return false;
        }

        return true;
    }
}

