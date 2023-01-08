package nu.marginalia.wmsa.edge.search.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.search.model.BrowseResultSet;
import nu.marginalia.wmsa.edge.search.results.BrowseResultCleaner;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;

@Singleton
public class IndexCommand {

    private final EdgeDataStoreDao dataStoreDao;
    private final BrowseResultCleaner browseResultCleaner;
    private final MustacheRenderer<BrowseResultSet> template;
    private final EdgeDomainBlacklist blacklist;
    @Inject
    public IndexCommand(EdgeDataStoreDao dataStoreDao, RendererFactory rendererFactory, BrowseResultCleaner browseResultCleaner, EdgeDomainBlacklist blacklist) throws IOException {
        this.dataStoreDao = dataStoreDao;
        this.browseResultCleaner = browseResultCleaner;

        template = rendererFactory.renderer("edge/index");
        this.blacklist = blacklist;
    }

    public String render(Request request, Response response) {
        response.header("Cache-control", "public,max-age=3600");

        var results = dataStoreDao.getRandomDomains(5, blacklist, 0);
        results.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

        return template.render(new BrowseResultSet(results.stream().limit(1).toList()));
    }
}
