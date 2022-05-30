package nu.marginalia.wmsa.edge.search.command;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.search.command.commands.*;

import java.util.ArrayList;
import java.util.List;

public class CommandEvaluator {

    private final List<SearchCommandInterface> commands = new ArrayList<>();
    private final SearchCommand search;

    @Inject
    public CommandEvaluator(
            BrowseCommand browse,
            ConvertCommand convert,
            DefinitionCommand define,
            SiteSearchCommand site,
            BangCommand bang,
            SearchCommand search
    ) {
        commands.add(browse);
        commands.add(convert);
        commands.add(define);
        commands.add(site);
        commands.add(bang);

        this.search = search;
    }

    public Object eval(Context ctx, SearchParameters parameters, String query) {
        for (var cmd : commands) {
            var ret = cmd.process(ctx, parameters, query);
            if (ret.isPresent()) {
                return ret.get();
            }
        }

        // Always process the search command last
        return search.process(ctx, parameters, query)
                .orElseThrow(() -> new IllegalStateException("Search Command returned Optional.empty()!") /* This Should Not be Possible™ */ );
    }

}
