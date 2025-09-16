package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.search.model.SearchParameters;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LangCommand implements SearchCommandInterface {

    private final Pattern queryPatternPredicate = Pattern.compile("(^|\\s)lang:([a-z]{2})(\\s|$)");
    private final LanguageConfiguration languageConfiguration;

    @Inject
    public LangCommand(LanguageConfiguration languageConfiguration) {
        this.languageConfiguration = languageConfiguration;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) {

        String query = parameters.query();
        Matcher matcher = queryPatternPredicate.matcher(query);

        if (!matcher.find())
            return Optional.empty();

        // Extract the language ISO code
        String langIsoCode = matcher.group(2).toLowerCase();

        if (languageConfiguration.getLanguage(langIsoCode) == null)
            return Optional.empty();

        String newUrl = parameters
                .withLanguage(langIsoCode)
                .withQuery(matcher.replaceAll(" ").trim())
                .renderUrl();

        return Optional.of(
                new MapModelAndView("redirect.jte", Map.of("url", newUrl))
        );
    }

}
