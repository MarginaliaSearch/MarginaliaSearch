package nu.marginalia.wmsa.edge.search.command;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.search.command.commands.*;

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
            SiteSearchCommand site,
            BangCommand bang,
            SearchCommand search
    ) {
        specialCommands.add(browse);
        specialCommands.add(convert);
        specialCommands.add(define);
        specialCommands.add(site);
        specialCommands.add(bang);

        defaultCommand = search;
    }

    public Object eval(Context ctx, SearchParameters parameters, String query) {
        for (var cmd : specialCommands) {
            var ret = cmd.process(ctx, parameters, query);
            if (ret.isPresent()) {
                return ret.get();
            }
        }

        // Always process the search command last
        return defaultCommand.process(ctx, parameters, query)
                .orElseThrow(() -> new IllegalStateException("Search Command returned Optional.empty()!") /* This Should Not be Possible™ */ );
    }

}
