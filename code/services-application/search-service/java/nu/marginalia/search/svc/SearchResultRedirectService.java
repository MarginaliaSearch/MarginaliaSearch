package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.StatusCode;
import io.jooby.annotation.*;
import nu.marginalia.api.searchquery.IndexUrlClient;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.model.NavbarModel;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class SearchResultRedirectService {
    private static final String realIpHeader = System.getProperty("system.realIpHeader", "X-Forwarded-For");
    private static final String salt = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);

    private final IndexUrlClient urlClient;
    private final ScrapeStopper scrapeStopper;

    private static volatile boolean isEnabled = false;

    @Inject
    public SearchResultRedirectService(IndexUrlClient urlClient,
                                       ScrapeStopper scrapeStopper) {
        this.urlClient = urlClient;
        this.scrapeStopper = scrapeStopper;

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                () -> isEnabled = scrapeStopper.isStrained(),
                60,
                60,
                TimeUnit.SECONDS
        );
    }

    public static boolean isEnabled() {
        return isEnabled;
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
        String ip = context.header(realIpHeader).valueOrNull();
        if (ip == null) {
            ip = context.getRemoteAddress();
        }

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
    public static String encode(Context ctx, int node, long docId) {
        String ip = ctx.header(realIpHeader).valueOrNull();
        long time = System.currentTimeMillis();

        return encode(ip, time, node, docId);
    }

    public static String encode(String remoteIp, long time, int node, long docId) {

        var sj = new StringJoiner("/", "r/", "");

        sj.add(Integer.toString(node));
        sj.add(Long.toUnsignedString(docId));
        sj.add(Long.toUnsignedString(time,36));

        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");
            hasher.update(salt.getBytes());
            hasher.update(remoteIp.getBytes());
            hasher.update(sj.toString().getBytes());

            return sj.toString() + "?hash="+ Base64.getUrlEncoder().withoutPadding().encodeToString(hasher.digest());
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

        long timeDecoded = Long.parseUnsignedLong(time, 36);
        if (timeDecoded > System.currentTimeMillis() - 3600) {
            return false;
        }

        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");

            hasher.update(salt.getBytes());
            hasher.update(remoteIp.getBytes());
            hasher.update(sb.toString().getBytes());

            return Objects.equals(providedHash, Base64.getUrlEncoder().withoutPadding().encodeToString(hasher.digest()));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

