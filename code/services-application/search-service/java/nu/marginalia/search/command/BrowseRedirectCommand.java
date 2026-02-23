package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.search.model.SearchParameters;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class BrowseRedirectCommand implements SearchCommandInterface {
    private final Predicate<String> queryPatternPredicate = Pattern.compile("^browse:[.A-Za-z\\-0-9:]+$").asPredicate();

    @Inject
    public BrowseRedirectCommand() {}

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters, Context ctx) {
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

        return Optional.of(
                new MapModelAndView("redirect.jte", Map.of("url", redirectPath))
        );
    }


}
