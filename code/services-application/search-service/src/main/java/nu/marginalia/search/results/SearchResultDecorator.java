package nu.marginalia.search.results;

import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import lombok.SneakyThrows;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.index.client.model.results.DecoratedSearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.search.model.PageScoreAdjustment;
import nu.marginalia.search.model.UrlDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SearchResultDecorator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @SneakyThrows
    public List<UrlDetails> getAllUrlDetails(List<DecoratedSearchResultItem> resultSet) {
        List<UrlDetails> ret = new ArrayList<>(resultSet.size());
        for (var detail : resultSet) {
            ret.add(new UrlDetails(
                    detail.documentId(),
                    detail.domainId(),
                    detail.url,
                    detail.title,
                    detail.description,
                    detail.urlQuality,
                    detail.wordsTotal,
                    detail.format,
                    detail.features,
                    "",
                    DomainIndexingState.ACTIVE,
                    detail.dataHash,
                    PageScoreAdjustment.zero(), // urlQualityAdjustment
                    detail.rankingId(),
                    detail.rankingScore, // termScore
                    detail.resultsFromDomain(),
                    getPositionsString(detail.rawIndexResult),
                    detail.rawIndexResult,
                    detail.rawIndexResult.keywordScores,
                    0L
                    ));
        }

        return ret;
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
}
