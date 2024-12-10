package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import spark.Response;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class BrowseRedirectCommand implements SearchCommandInterface {
    private final Predicate<String> queryPatternPredicate = Pattern.compile("^browse:[.A-Za-z\\-0-9:]+$").asPredicate();

    @Inject
    public BrowseRedirectCommand()
    {

    }

    @Override
    public Optional<Object> process(Response response, SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return Optional.empty();
        }

        String definePrefix = "browse:";
        String word = parameters.query().substring(definePrefix.length()).toLowerCase();

        String redirectPath;

        if (word.equals("random")) {
            redirectPath = "/explore";
        } else {
            redirectPath = "/explore/" + word;
        }

        return Optional.of("""
                <!DOCTYPE html>
                <html lang="en">
                <meta charset="UTF-8">
                <title>Redirecting...</title>
                <meta http-equiv="refresh" content="0; %s">
                """.formatted(redirectPath));
    }


}
