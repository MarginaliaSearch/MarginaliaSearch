package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.MapModelAndView;
import io.jooby.SameSite;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.svc.SearchSiteInfoService;
import nu.marginalia.service.server.RateLimiter;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Singleton
public class ScrapeStopperInterceptor {

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

        String remoteIp = context.header("X-Forwarded-For").valueOrNull();

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
            sst = scrapeStopper.getToken(zone,
                    remoteIp,
                    Duration.ofSeconds(3),
                    Duration.ofMinutes(5),
                    ThreadLocalRandom.current().nextInt(10, 50));
        }

        return new InterceptRedirect(sst,
                scrapeStopper.getRemaining(sst).orElseThrow());
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

    public record InterceptRedirect(String sst, Duration waitTime) implements InterceptionResult {}
    public record InterceptPass(String sst) implements InterceptionResult {}
    public record InterceptPrefetch(String sst) implements InterceptionResult {}
}
