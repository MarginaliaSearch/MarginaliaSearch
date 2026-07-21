package nu.marginalia.index.searchset;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.index.IndexFactory;
import nu.marginalia.ranking.set.RankingSearchSet;
import nu.marginalia.ranking.set.SearchSet;
import nu.marginalia.ranking.set.SearchSetAny;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Query-side access to the ranking search sets.  The sets themselves are calculated by the
 * ranking constructor process, this service only loads the persisted results from disk.
 */
@Singleton
public class SearchSetsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IndexFactory indexFactory;
    private final DomainRankingSetsService domainRankingSetsService;

    private final ConcurrentHashMap<String, SearchSet> rankingSets = new ConcurrentHashMap<>();
    // Below are binary indices that are used to constrain a search
    private final SearchSet anySet = new SearchSetAny();

    @Inject
    public SearchSetsService(IndexFactory indexFactory,
                             DomainRankingSetsService domainRankingSetsService) throws IOException {
        this.indexFactory = indexFactory;
        this.domainRankingSetsService = domainRankingSetsService;

        loadSets();
    }

    public SearchSet getSearchSetByName(String searchSetIdentifier) {

        if (null == searchSetIdentifier
           || searchSetIdentifier.isBlank()
           || "NONE".equals(searchSetIdentifier))
        {
            return anySet;
        }

        return Objects.requireNonNull(rankingSets.get(searchSetIdentifier), "Unknown search set");
    }

    /** Reload the search sets from disk, after the ranking constructor process has written
     * new versions. */
    public void reload() throws IOException {
        loadSets();

        logger.info("Reloaded {} search sets", rankingSets.size());
    }

    private void loadSets() throws IOException {
        for (DomainRankingSetsService.DomainRankingSet rankingSet : domainRankingSetsService.getAll()) {
            rankingSets.put(rankingSet.name(),
                    new RankingSearchSet(rankingSet.name(),
                            rankingSet.fileName(indexFactory.getSearchSetsBase())
                    )
            );
        }
    }
}
