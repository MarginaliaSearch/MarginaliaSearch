package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.client.Context;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SiteRedirectCommand implements SearchCommandInterface {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Predicate<String> queryPatternPredicate = Pattern.compile("^site:[.A-Za-z\\-0-9]+$").asPredicate();

    @Inject
    public SiteRedirectCommand() {
    }

    @SneakyThrows
    @Override
    public boolean process(Context ctx, Response response, SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return false;
        }

        String definePrefix = "site:";
        String domain = parameters.query().substring(definePrefix.length()).toLowerCase();

        // Use an HTML redirect here, so we can use relative URLs

        response.raw().getOutputStream().println("""
                <!DOCTYPE html>
                <html lang="en">
                <meta charset="UTF-8">
                <title>Redirecting...</title>
                <meta http-equiv="refresh" content="0; url=/site/%s">
                """.formatted(domain));

        return true;
    }

}
