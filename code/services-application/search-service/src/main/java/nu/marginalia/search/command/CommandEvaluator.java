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
            if (cmd.process(ctx, response, parameters)) {
                // The commands will write directly to the response, so we don't need to do anything else
                // but it's important we don't return null, as this signals to Spark that we haven't handled
                // the request.

                return "";
            }
        }

        defaultCommand.process(ctx, response, parameters);
        return "";
    }

}
