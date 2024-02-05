package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.index.client.model.results.DecoratedSearchResultItem;
import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.ranking.ResultValuator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Joins the index view of a set of search results with data from the linkdb */
@Singleton
public class IndexResultDecorator {

    private static final Logger logger = LoggerFactory.getLogger(IndexResultDecorator.class);

    private final DocumentDbReader documentDbReader;
    private final ResultValuator valuator;

    @Inject
    public IndexResultDecorator(DocumentDbReader documentDbReader,
                                ResultValuator valuator) {
        this.documentDbReader = documentDbReader;
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

        Map<Long, DocdbUrlDetail> urlDetailsById = new HashMap<>(rawResults.size());

        for (var item : documentDbReader.getUrlDetails(idsList))
            urlDetailsById.put(item.urlId(), item);

        List<DecoratedSearchResultItem> decoratedItems = new ArrayList<>();
        for (var result : rawResults) {
            var docData = urlDetailsById.get(result.getDocumentId());

            if (null == docData) {
                logger.warn("No data for document id {}", result.getDocumentId());
                continue;
            }

            decoratedItems.add(createCombinedItem(result, docData, rankingContext));
        }

        if (decoratedItems.size() != rawResults.size())
            logger.warn("Result list shrunk during decoration?");

        return decoratedItems;
    }

    private DecoratedSearchResultItem createCombinedItem(SearchResultItem result,
                                                         DocdbUrlDetail docData,
                                                         ResultRankingContext rankingContext) {
        return new DecoratedSearchResultItem(
                result,
                docData.url(),
                docData.title(),
                docData.description(),
                docData.urlQuality(),
                docData.format(),
                docData.features(),
                docData.pubYear(),
                docData.dataHash(),
                docData.wordsTotal(),
                valuator.calculateSearchResultValue(result.keywordScores, docData.wordsTotal(), rankingContext)
        );

    }
}
