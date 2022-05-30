package nu.marginalia.wmsa.edge.search.command;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;

import java.util.ArrayList;
import java.util.List;

public class CommandEvaluator {

    List<SearchCommandInterface> commands = new ArrayList<>();


    @Inject
    public CommandEvaluator(
            BrowseCommand browse,
            ConvertCommand convert,
            DefinitionCommand define,
            SiteSearchCommand site,
            SearchCommand search
    ) {
        commands.add(browse);
        commands.add(convert);
        commands.add(define);
        commands.add(site);
        commands.add(search);
    }

    public Object eval(Context ctx, SearchParameters parameters, String query) {
        for (var cmd : commands) {
            var ret = cmd.process(ctx, parameters, query);
            if (ret.isPresent()) {
                return ret.get();
            }
        }
        // Search command *should* always evaluate
        throw new IllegalStateException("Search Command returned Optional.empty()");
    }

}
