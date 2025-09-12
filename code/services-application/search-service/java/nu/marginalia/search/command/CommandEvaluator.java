package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.ModelAndView;
import nu.marginalia.search.command.commands.*;
import nu.marginalia.search.model.SearchParameters;

import java.util.ArrayList;
import java.util.List;

public class CommandEvaluator {

    private final List<SearchCommandInterface> specialCommands = new ArrayList<>();
    private final SearchCommand defaultCommand;

    @Inject
    public CommandEvaluator(
            BrowseRedirectCommand browse,
            ConvertCommand convert,
            DefinitionCommand define,
            BangCommand bang,
            SiteRedirectCommand siteRedirect,
            SearchCommand search
    ) {
        specialCommands.add(browse);
        specialCommands.add(convert);
        specialCommands.add(define);
        specialCommands.add(bang);
        specialCommands.add(siteRedirect);

        defaultCommand = search;
    }

    public ModelAndView<?> eval(SearchParameters parameters) throws Exception {
        for (var cmd : specialCommands) {
            var maybe = cmd.process(parameters);
            if (maybe.isPresent())
                return maybe.get();
        }

        return defaultCommand.process(parameters).orElseThrow();
    }

}
