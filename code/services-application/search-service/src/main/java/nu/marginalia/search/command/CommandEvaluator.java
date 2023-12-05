package nu.marginalia.search.command;

import com.google.inject.Inject;
import nu.marginalia.search.command.commands.*;
import nu.marginalia.client.Context;
import spark.Response;

import java.util.ArrayList;
import java.util.List;

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

    public Object eval(Context ctx, Response response, SearchParameters parameters) {
        for (var cmd : specialCommands) {
            var maybe = cmd.process(ctx, response, parameters);
            if (maybe.isPresent())
                return maybe.get();
        }

        return defaultCommand.process(ctx, response, parameters).orElse("");
    }

}
