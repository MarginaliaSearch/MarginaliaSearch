package nu.marginalia.search.results;

import com.google.inject.Inject;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.index.client.model.results.SearchResultRankingContext;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.ranking.ResultValuator;
import nu.marginalia.search.db.DbUrlDetailsQuery;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.id.EdgeIdList;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.search.model.UrlDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SearchResultDecorator {
    private final DbUrlDetailsQuery dbUrlDetailsQuery;
    private final ResultValuator valuator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchResultDecorator(DbUrlDetailsQuery dbUrlDetailsQuery,
                                 ResultValuator valuator) {
        this.dbUrlDetailsQuery = dbUrlDetailsQuery;
        this.valuator = valuator;
    }

    public List<UrlDetails> getAllUrlDetails(SearchResultSet resultSet) {
        TIntObjectHashMap<UrlDetails> detailsById = new TIntObjectHashMap<>(resultSet.size());

        EdgeIdList<EdgeUrl> idList = resultSet.results.stream()
                                                .mapToInt(SearchResultItem::getUrlIdInt)
                                                .collect(EdgeIdList::new, EdgeIdList::add, EdgeIdList::addAll);

        List<UrlDetails> ret = dbUrlDetailsQuery.getUrlDetailsMulti(idList);

        for (var val : ret) {
            detailsById.put(val.id, val);
        }

        List<UrlDetails> retList = new ArrayList<>(resultSet.size());

        TIntArrayList missedIds = new TIntArrayList();
        for (var resultItem : resultSet.results) {

            var rankingId = resultItem.getRanking();
            var uid = resultItem.getUrlId().id();

            var details = detailsById.get(uid);
            if (details == null) {
                missedIds.add(uid);
                continue;
            }

            details.rankingId = rankingId;

            details.resultsFromSameDomain = resultItem.resultsFromDomain;
            details.termScore = calculateTermScore(resultItem, details, resultSet.rankingContext);
            if (getClass().desiredAssertionStatus()) {
                details.keywordScores = resultItem.keywordScores;
            }
            details.positions = getPositionsString(resultItem);
            details.resultItem = resultItem;

            retList.add(details);
        }
        if (!missedIds.isEmpty()) {
            logger.info("Could not look up documents: {}", missedIds.toArray());
        }

        return retList;
    }

    private String getPositionsString(SearchResultItem resultItem) {
        Int2LongArrayMap positionsPerSet = new Int2LongArrayMap(8);

        for (var score : resultItem.keywordScores) {
            if (!score.isKeywordRegular()) {
                continue;
            }
            positionsPerSet.merge(score.subquery(), score.positions(), this::and);
        }

        long bits = positionsPerSet.values().longStream().reduce(this::or).orElse(0);

        return BrailleBlockPunchCards.printBits(bits, 56);

    }

    private long and(long a, long b) {
        return a & b;
    }
    private long or(long a, long b) {
        return a | b;
    }

    private double calculateTermScore(SearchResultItem resultItem, UrlDetails details, SearchResultRankingContext rankingContext) {

        final double statePenalty = (details.domainState == DomainIndexingState.SPECIAL) ? 1.25 : 0;

        final double value = valuator.calculateSearchResultValue(resultItem.keywordScores,
                details.words,
                details.title.length(),
                rankingContext);

        return value + statePenalty;
    }

}
