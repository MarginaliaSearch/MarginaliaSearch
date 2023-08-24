package nu.marginalia.search.results;

import com.google.inject.Inject;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import lombok.SneakyThrows;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.ranking.ResultValuator;
import nu.marginalia.search.model.PageScoreAdjustment;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.linkdb.LinkdbReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SearchResultDecorator {
    private final LinkdbReader linkDbReader;
    private final ResultValuator valuator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchResultDecorator(LinkdbReader linkDbReader,
                                 ResultValuator valuator) {
        this.linkDbReader = linkDbReader;
        this.valuator = valuator;
    }

    @SneakyThrows
    public List<UrlDetails> getAllUrlDetails(SearchResultSet resultSet) {
        TLongObjectHashMap<UrlDetails> detailsById = new TLongObjectHashMap<>(resultSet.size());

        TLongArrayList idsList = new TLongArrayList(resultSet.results.size());
        for (var result : resultSet.results) {
            idsList.add(result.getDocumentId());
        }

        List<UrlDetails> ret = new ArrayList<>(idsList.size());
        for (var rawDetail : linkDbReader.getUrlDetails(idsList)) {
            ret.add(new UrlDetails(
                    rawDetail.urlId(),
                    UrlIdCodec.getDomainId(rawDetail.urlId()),
                    rawDetail.url(),
                    rawDetail.title(),
                    rawDetail.description(),
                    rawDetail.urlQuality(),
                    rawDetail.wordsTotal(),
                    rawDetail.format(),
                    rawDetail.features(),
                    "",
                    DomainIndexingState.ACTIVE,
                    rawDetail.dataHash(),
                    PageScoreAdjustment.zero(), // urlQualityAdjustment
                    Integer.MAX_VALUE, // rankingId
                    Double.MAX_VALUE, // termScore
                    1, // resultsFromSameDomain
                    "", // positions
                    null, // result item
                    null // keyword scores
                    ));
        }

        for (var val : ret) {
            detailsById.put(val.id, val);
        }

        List<UrlDetails> retList = new ArrayList<>(resultSet.size());

        TLongArrayList missedIds = new TLongArrayList();
        for (var resultItem : resultSet.results) {

            var rankingId = resultItem.getRanking();
            var uid = resultItem.getDocumentId();

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

    private double calculateTermScore(SearchResultItem resultItem, UrlDetails details, ResultRankingContext rankingContext) {

        final double statePenalty = (details.domainState == DomainIndexingState.SPECIAL) ? 1.25 : 0;

        final double value = valuator.calculateSearchResultValue(resultItem.keywordScores,
                details.words,
                rankingContext);

        return value + statePenalty;
    }

}
