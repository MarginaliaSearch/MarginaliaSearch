package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SearchErrorMessageModel;
import nu.marginalia.search.model.SearchFilters;
import nu.marginalia.search.model.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class SearchErrorPageService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LanguageConfiguration languageConfiguration;

    @Inject
    public SearchErrorPageService(LanguageConfiguration languageConfiguration) throws IOException {
        this.languageConfiguration = languageConfiguration;
    }

    public ModelAndView<?> serveError(SearchParameters parameters) {

        return new MapModelAndView("serp/error.jte",
                Map.of("navbar", NavbarModel.LIMBO,
                        "model", new SearchErrorMessageModel(
                                "An error occurred when communicating with the search engine index.",
                                """
                                            This is hopefully a temporary state of affairs.  It may be due to
                                            an upgrade.  The index typically takes a about two or three minutes
                                            to reload from a cold restart.  Thanks for your patience.
                                            """,
                                parameters,
                                new SearchFilters(parameters)
                        ),
                        "languageDefinitions", languageConfiguration.languagesMap()
                    )
                );
    }

}
