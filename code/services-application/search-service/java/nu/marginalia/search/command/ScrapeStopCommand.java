package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SearchFilters;
import nu.marginalia.search.model.SearchParameters;
import nu.marginalia.service.server.RateLimiter;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class ScrapeStopCommand implements SearchCommandInterface {

    private final ScrapeStopper scrapeStopper;
    private final LanguageConfiguration languageConfiguration;

    private final RateLimiter rateLimiter = RateLimiter.queryPerMinuteLimiter(30);

    @Inject
    public ScrapeStopCommand(ScrapeStopper scrapeStopper,
                             LanguageConfiguration languageConfiguration)
    {
        this.scrapeStopper = scrapeStopper;
        this.languageConfiguration = languageConfiguration;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters, Context ctx) {
        if (rateLimiter.isAllowed())
            return Optional.empty();

        String remoteIp = ctx.header("X-Forwarded-For").valueOrNull();

        String token = parameters.scrapeStopperToken();
        ScrapeStopper.TokenState state = scrapeStopper.validateToken(token, remoteIp);

        if (state == ScrapeStopper.TokenState.VALIDATED)
            return Optional.empty();

        else if (state == ScrapeStopper.TokenState.EARLY) {
            Duration waitDuration = scrapeStopper.getRemaining(token).orElseThrow(IllegalStateException::new);

            ctx.setResponseHeader("Cache-Control", "no-store");

            return Optional.of(
                    new MapModelAndView("serp/wait.jte",
                            Map.of(
                                    "waitDuration", waitDuration,
                                    "parameters", parameters,
                                    "filters", new SearchFilters(parameters),
                                    "navbar", NavbarModel.SEARCH,
                                    "requestMethod", parameters.requestMethod(),
                                    "displayUrl", parameters.renderUrl(),
                                    "languageDefinitions", languageConfiguration.languagesMap()
                            )
                    )
            );
        }
        else if (state == ScrapeStopper.TokenState.INVALID) {

            token = scrapeStopper.getToken("SEARCH",
                    remoteIp,
                    Duration.ofSeconds(3),
                    Duration.ofMinutes(1),
                    5);

            Duration waitDuration = scrapeStopper.getRemaining(token).orElseThrow(IllegalStateException::new);

            ctx.setResponseHeader("Cache-Control", "no-store");

            parameters = parameters.withSst(token);

            return Optional.of(
                    new MapModelAndView("serp/wait.jte",
                            Map.of(
                                    "waitDuration", waitDuration,
                                    "parameters", parameters,
                                    "filters", new SearchFilters(parameters),
                                    "navbar", NavbarModel.SEARCH,
                                    "requestMethod", parameters.requestMethod(),
                                    "displayUrl", parameters.renderUrl(),
                                    "languageDefinitions", languageConfiguration.languagesMap()
                            )
                    )
            );
        }

        throw new IllegalStateException("Unreachable code");
    }

}
