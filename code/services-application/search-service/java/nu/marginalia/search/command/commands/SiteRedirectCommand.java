package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SiteRedirectCommand implements SearchCommandInterface {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Predicate<String> queryPatternPredicate = Pattern.compile("^(site|links):[.A-Za-z\\-0-9]+$").asPredicate();

    @Inject
    public SiteRedirectCommand() {
    }

    @Override
    public Optional<Object> process(Response response, SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return Optional.empty();
        }

        int idx = parameters.query().indexOf(':');
        String prefix = parameters.query().substring(0, idx);
        String domain = parameters.query().substring(idx + 1).toLowerCase();

        // Use an HTML redirect here, so we can use relative URLs
        String view = switch (prefix) {
            case "links" -> "links";
            default -> "info";
        };

        return Optional.of("""
                <!DOCTYPE html>
                <html lang="en">
                <meta charset="UTF-8">
                <title>Redirecting...</title>
                <meta http-equiv="refresh" content="0; url=/site/%s?view=%s">
                """.formatted(domain, view)
        );
    }

}
