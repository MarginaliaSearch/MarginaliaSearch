package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.service.server.RateLimiter;
import org.jetbrains.annotations.Nullable;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

@Singleton
public class ScrapeStopperInterceptor {

    private final boolean isEnabled = Boolean.getBoolean("search.useScrapeStopper");
    private final boolean isRerollEnabled = Boolean.getBoolean("search.scrapeStopper.rerollSst");

    private final double pReroll = 0.75;

    private final ScrapeStopper scrapeStopper;
    private final RendererFactory rendererFactory;
    private final MustacheRenderer<Object> waitRenderer;

    @Inject
    public ScrapeStopperInterceptor(ScrapeStopper scrapeStopper, RendererFactory rendererFactory)
            throws IOException
    {
        this.scrapeStopper = scrapeStopper;
        this.rendererFactory = rendererFactory;

        this.waitRenderer = rendererFactory.renderer("search/wait-page");
    }

    public InterceptionResult intercept(String zone,
                                        @Nullable
                                        String zoneContext,
                                        RateLimiter limiter,
                                        Request request,
                                        Response response)
    {
        if (!isEnabled)
            return new InterceptPass("");

        String remoteIp = request.headers("X-Forwarded-For");
        String sst = request.queryParamOrDefault("sst", "");
        ScrapeStopper.TokenState tokenState = scrapeStopper.validateToken(sst, remoteIp, zoneContext);

        if (limiter.isAllowed())
            return new InterceptPass(sst);

        if (tokenState == ScrapeStopper.TokenState.VALIDATED) {
            if (isRerollEnabled && ThreadLocalRandom.current().nextDouble() > pReroll) {
                var newSst = scrapeStopper.relocateToken(sst, zone);

                // Concurrent relocates, let's revalidate this token
                if (newSst.isEmpty())
                    return intercept(zone, zoneContext, limiter, request, response);

                sst = newSst.get();

            }

            return new InterceptPass(sst);
        }

        if (tokenState == ScrapeStopper.TokenState.INVALID)
            sst = scrapeStopper.getToken(zone,
                    remoteIp,
                    Duration.ofSeconds(3),
                    Duration.ofMinutes(5),
                    ThreadLocalRandom.current().nextInt(10, 50));

        response.header("Cache-Control", "no-store");

        return new InterceptRedirect(sst,
                waitRenderer.render(Map.of(
                "waitDuration", (int) scrapeStopper.getRemaining(sst).orElseThrow().toSeconds() + 1,
                "redirUrl", constructRedirectUrl(sst, request)
        )));
    }

    private String constructRedirectUrl(String sst, Request request) {

        StringJoiner redirUrlBuilder = new StringJoiner("&", "?", "");

        for (String name: request.queryParams()) {
            if ("sst".equalsIgnoreCase(name))
                continue;

            String val = request.queryParams(name);
            String valEncoded = URLEncoder.encode(val, StandardCharsets.UTF_8);
            redirUrlBuilder.add(name + "=" + valEncoded);
        }

        // Finally add a new SST
        redirUrlBuilder.add("sst=" + URLEncoder.encode(sst, StandardCharsets.UTF_8));

        return redirUrlBuilder.toString();
    }

    public sealed interface InterceptionResult {
        String sst();
    }

    public record InterceptRedirect(String sst, Object result) implements InterceptionResult {}
    public record InterceptPass(String sst) implements InterceptionResult {}
}
