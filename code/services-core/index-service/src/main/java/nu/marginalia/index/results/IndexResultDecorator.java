package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.index.client.model.results.DecoratedSearchResultItem;
import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.linkdb.LinkdbReader;
import nu.marginalia.linkdb.model.LdbUrlDetail;
import nu.marginalia.ranking.ResultValuator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Joins the index view of a set of search results with data from the linkdb */
@Singleton
public class IndexResultDecorator {

    private final LinkdbReader linkdbReader;
    private final ResultValuator valuator;

    @Inject
    public IndexResultDecorator(LinkdbReader linkdbReader,
                                ResultValuator valuator) {
        this.linkdbReader = linkdbReader;
        this.valuator = valuator;
    }

    /** Decorate the result items with additional information from the link database
     * and calculate an updated ranking with the additional information */
    public List<DecoratedSearchResultItem> decorateAndRerank(List<SearchResultItem> rawResults,
                                                             ResultRankingContext rankingContext)
            throws SQLException
    {
        TLongList idsList = new TLongArrayList(rawResults.size());

        for (var result : rawResults)
            idsList.add(result.getDocumentId());

        Map<Long, LdbUrlDetail> urlDetailsById = new HashMap<>(rawResults.size());

        for (var item : linkdbReader.getUrlDetails(idsList))
            urlDetailsById.put(item.urlId(), item);

        List<DecoratedSearchResultItem> decoratedItems = new ArrayList<>();
        for (var result : rawResults) {
            var linkData = urlDetailsById.get(result.getDocumentId());
            decoratedItems.add(createCombinedItem(result, linkData, rankingContext));
        }

        assert decoratedItems.size() == rawResults.size() : "Result list shrunk during decoration?";

        return decoratedItems;
    }

    private DecoratedSearchResultItem createCombinedItem(SearchResultItem result,
                                                         LdbUrlDetail linkData,
                                                         ResultRankingContext rankingContext) {
        return new DecoratedSearchResultItem(
                result,
                linkData.url(),
                linkData.title(),
                linkData.description(),
                linkData.urlQuality(),
                linkData.format(),
                linkData.features(),
                linkData.pubYear(),
                linkData.dataHash(),
                linkData.wordsTotal(),
                valuator.calculateSearchResultValue(result.keywordScores, linkData.wordsTotal(), rankingContext)
        );

    }
}
