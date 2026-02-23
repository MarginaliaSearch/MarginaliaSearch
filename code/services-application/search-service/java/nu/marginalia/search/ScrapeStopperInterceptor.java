package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.svc.SearchSiteInfoService;
import nu.marginalia.service.server.RateLimiter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Singleton
public class ScrapeStopperInterceptor {

    private final ScrapeStopper scrapeStopper;
    private final double pReroll = 0.75;

    @Inject
    public ScrapeStopperInterceptor(ScrapeStopper scrapeStopper)
    {
        this.scrapeStopper = scrapeStopper;
    }

    public InterceptionResult intercept(String zone,
                                RateLimiter limiter,
                                        Context context,
                                        String sst)
    {
        if (limiter.isAllowed()) {
            return new InterceptPass(sst);
        }

        String remoteIp = context.header("X-Forwarded-For").valueOrNull();

        ScrapeStopper.TokenState tokenState = scrapeStopper.validateToken(sst, remoteIp);
        if (tokenState == ScrapeStopper.TokenState.VALIDATED) {
            if (ThreadLocalRandom.current().nextDouble() > pReroll) {
                var newSst = scrapeStopper.relocateToken(sst, zone);

                // Concurrent relocates, let's revalidate this token
                if (newSst.isEmpty())
                    return intercept(zone, limiter, context, sst);

                sst = newSst.get();
            }

            return new InterceptPass(sst);
        }

        context.setResponseHeader("Cache-Control", "no-store");

        if (tokenState == ScrapeStopper.TokenState.INVALID) {
            sst = scrapeStopper.getToken(zone,
                    remoteIp,
                    Duration.ofSeconds(3),
                    Duration.ofMinutes(5),
                    ThreadLocalRandom.current().nextInt(8, 20));
        }

        return new InterceptRedirect(sst,
                scrapeStopper.getRemaining(sst).orElseThrow());
    }

    public InterceptionResult intercept(String zone,
                                        RateLimiter limiter,
                                        Context context) {
        String sst = context.query("sst").value("");
        return intercept(zone, limiter, context, sst);
    }

    public sealed interface InterceptionResult {
        String sst();
    }

    public record InterceptRedirect(String sst, Duration waitTime) implements InterceptionResult {}
    public record InterceptPass(String sst) implements InterceptionResult {}
}
