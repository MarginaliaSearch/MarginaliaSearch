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

    private final Pattern queryPatternPredicate = Pattern.compile("(^|\\s)lang:[a-z]{2}(\\s|$)");
    private final LanguageConfiguration languageConfiguration;

    @Inject
    public LangCommand(LanguageConfiguration languageConfiguration) {
        this.languageConfiguration = languageConfiguration;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) {

        String query = parameters.query();
        Matcher matcher = queryPatternPredicate.matcher(query);
        if (matcher.find()) {
            String lang = query.substring(matcher.start(), matcher.end()).trim().toLowerCase();
            if (lang.length() <= 2)
                return Optional.empty();

            lang = lang.substring(lang.length() - 2, lang.length());
            if (lang.equalsIgnoreCase(parameters.languageIsoCode()))
                return Optional.empty();

            if (languageConfiguration.getLanguage(lang) == null)
                return Optional.empty();

            StringBuilder newQuery = new StringBuilder(query);
            newQuery.replace(matcher.start(), matcher.end(), " ");

            String newUrl = parameters.withLanguage(lang).withQuery(newQuery.toString().trim()).renderUrl();
            return Optional.of(
                    new MapModelAndView("redirect.jte", Map.of("url", newUrl))
            );
        }

        return Optional.empty();
    }

}
