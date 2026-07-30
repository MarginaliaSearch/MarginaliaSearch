package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.SameSite;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.service.server.RateLimiter;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

@Singleton
public class ScrapeStopperInterceptor {

    private final String realIpHeader = System.getProperty("system.realIpHeader", "X-Forwarded-For");
    private final boolean isEnabled = Boolean.getBoolean("search.useScrapeStopper");
    private final boolean isRerollEnabled = Boolean.getBoolean("search.scrapeStopper.rerollSst");

    private final double pReroll = 0.75;

    private final ScrapeStopper scrapeStopper;

    @Inject
    public ScrapeStopperInterceptor(ScrapeStopper scrapeStopper)
    {
        this.scrapeStopper = scrapeStopper;
    }

    public InterceptionResult intercept(String zone,
                                        @Nullable
                                        String zoneContext,
                                        RateLimiter limiter,
                                        Context context,
                                        String sst)
    {

        // Regardless whether enabled, we don't want to honor Sec-Purpose requests
        if (context.header("Sec-Purpose").isPresent()) {
            return new InterceptPrefetch(sst);
        }

        if (!isEnabled) {
            return new InterceptPass(sst);
        }

        String cookieName = "sst-"+zone;

        if (null == sst || sst.isBlank()) {
            sst = context.cookie(cookieName).valueOrNull();
        }
        else if (!sst.equals(context.cookie(cookieName).valueOrNull())) {
            context.setResponseCookie(
                    new Cookie(cookieName, sst)
                            .setMaxAge(Duration.ofMinutes(5))
                            .setSameSite(SameSite.STRICT)
            );
        }

        String remoteIp = context.header(realIpHeader).valueOrNull();

        ScrapeStopper.TokenState tokenState = scrapeStopper.validateToken(sst, remoteIp, zoneContext);

        if (tokenState == ScrapeStopper.TokenState.VALIDATED) {

            if (isRerollEnabled && ThreadLocalRandom.current().nextDouble() > pReroll) {
                var newSst = scrapeStopper.relocateToken(sst, zone);

                // Concurrent relocates, let's revalidate this token
                if (newSst.isEmpty())
                    return intercept(zone, zoneContext, limiter, context, sst);

                sst = newSst.get();
            }

            return new InterceptPass(sst);
        }
        else if (limiter.isAllowed()) {
            return new InterceptPass(sst);
        }

        context.setResponseHeader("Cache-Control", "no-store");

        if (tokenState == ScrapeStopper.TokenState.INVALID) {
            sst = scrapeStopper.getToken(zone, remoteIp, Duration.ofMinutes(5));
        }

        return new InterceptRedirect(sst,
                scrapeStopper.getRemaining(sst).orElseThrow(),
                constructRedirectQuery(context, sst));
    }

    private String constructRedirectQuery(Context context, String sst) {
        StringJoiner queryBuilder = new StringJoiner("&", "?", "");

        for (Map.Entry<String, String> param : context.queryMap().entrySet()) {
            if ("sst".equalsIgnoreCase(param.getKey()))
                continue;

            queryBuilder.add(urlEncode(param.getKey()) + "=" + urlEncode(param.getValue()));
        }

        queryBuilder.add("sst=" + urlEncode(sst));

        return queryBuilder.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public InterceptionResult intercept(String zone,
                                        @Nullable
                                        String zoneContext,
                                        RateLimiter limiter,
                                        Context context) {
        String sst = context.query("sst").value("");
        return intercept(zone, zoneContext, limiter, context, sst);
    }

    public sealed interface InterceptionResult {
        String sst();
    }

    public record InterceptRedirect(String sst, Duration waitTime, String redirUrl) implements InterceptionResult {}
    public record InterceptPass(String sst) implements InterceptionResult {}
    public record InterceptPrefetch(String sst) implements InterceptionResult {}
}
