package nu.marginalia.search.command;

import com.google.inject.Inject;
import nu.marginalia.search.command.commands.*;
import nu.marginalia.client.Context;

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
            SiteListCommand site,
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

    public Object eval(Context ctx, SearchParameters parameters) {
        for (var cmd : specialCommands) {
            var ret = cmd.process(ctx, parameters);
            if (ret.isPresent()) {
                return ret.get();
            }
        }

        // Always process the search command last
        return defaultCommand.process(ctx, parameters)
                .orElseThrow(() -> new IllegalStateException("Search Command returned Optional.empty()!") /* This Should Not be Possible™ */ );
    }

}
