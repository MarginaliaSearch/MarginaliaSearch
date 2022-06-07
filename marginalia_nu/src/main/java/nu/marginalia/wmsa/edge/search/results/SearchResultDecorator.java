package nu.marginalia.wmsa.edge.search.results;

import com.google.inject.Inject;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import io.reactivex.rxjava3.annotations.NonNull;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.results.model.TieredSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SearchResultDecorator {
    private final EdgeDataStoreDao edgeDataStoreDao;
    private final SearchResultValuator valuator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchResultDecorator(EdgeDataStoreDao edgeDataStoreDao, SearchResultValuator valuator) {
        this.edgeDataStoreDao = edgeDataStoreDao;
        this.valuator = valuator;
    }

    @NonNull
    public List<TieredSearchResult> decorateSearchResults(List<EdgeSearchResultItem> items, IndexBlock block, UrlDeduplicator deduplicator) {
        List<TieredSearchResult> results = new ArrayList<>();

        int dedups = 0;
        for (var details : getAllUrlDetails(items, block)) {
            if (deduplicator.filter(details)) {
                results.add(new TieredSearchResult(details.queryLength, getEffectiveBlock(details), details));
            }
            else {
                dedups++;
            }
        }
        if (dedups > 0) {
            logger.debug("dedups: {}", dedups);
        }

        return results;
    }


    private static final TreeMap<Double, IndexBlock> blocksByOrder = new TreeMap<>();
    static {
        for (var block : IndexBlock.values()) {
            blocksByOrder.put((double) block.sortOrder, block);
        }
    }

    private IndexBlock getEffectiveBlock(EdgeUrlDetails details) {
        return blocksByOrder.floorEntry(details.termScore).getValue();
    }

    private List<EdgeUrlDetails> getAllUrlDetails(List<EdgeSearchResultItem> resultItems, IndexBlock block) {
        TIntObjectHashMap<EdgeUrlDetails> detailsById = new TIntObjectHashMap<>(resultItems.size());

        var idList = resultItems.stream().map(EdgeSearchResultItem::getUrl).collect(Collectors.toList());

        List<EdgeUrlDetails> ret = edgeDataStoreDao.getUrlDetailsMulti(idList);

        for (var val : ret) {
            detailsById.put(val.id, val);
        }

        List<EdgeUrlDetails> retList = new ArrayList<>(resultItems.size());

        TIntArrayList missedIds = new TIntArrayList();
        for (var resultItem : resultItems) {

            var did = resultItem.getDomain().getId();
            var uid = resultItem.getUrl().getId();

            var details = detailsById.get(uid);
            if (details == null) {
                missedIds.add(uid);
                continue;
            }

            if (details.rankingId == Integer.MAX_VALUE) {
                details.rankingId = did;
            }

            details.termScore = calculateTermScore(block, resultItem, details);
            details.queryLength = resultItem.queryLength;

            logger.debug("{} -> {}", details.url, details.termScore);

            retList.add(details);
        }
        if (!missedIds.isEmpty()) {
            logger.warn("Could not look up documents: {}", missedIds.toArray());
        }
        retList.sort(Comparator.comparing(EdgeUrlDetails::getTermScore));
        return retList;
    }

    private double calculateTermScore(IndexBlock block, EdgeSearchResultItem resultItem, EdgeUrlDetails details) {
        return valuator.evaluateTerms(resultItem.scores, block, details.words) / Math.sqrt(1 + resultItem.queryLength)
                + ((details.domainState == EdgeDomainIndexingState.SPECIAL) ? 1.25 : 0);
    }

}
