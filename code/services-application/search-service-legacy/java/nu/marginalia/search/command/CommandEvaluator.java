package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.Context;
import nu.marginalia.search.command.commands.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommandEvaluator {

    private final List<SearchCommandInterface> specialCommands = new ArrayList<>();
    private final SearchCommand defaultCommand;

    @Inject
    public CommandEvaluator(
            BrowseCommand browse,
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

    public Object eval(SearchParameters parameters, Context context) {
        for (SearchCommandInterface cmd : specialCommands) {
            Optional<Object> maybe = cmd.process(parameters, context);
            if (maybe.isPresent())
                return maybe.get();
        }

        return defaultCommand.process(parameters, context).orElse("");
    }

}
