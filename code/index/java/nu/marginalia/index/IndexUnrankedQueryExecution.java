package nu.marginalia.index;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcRawResultItem;
import nu.marginalia.api.searchquery.RpcResultKeywordScore;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.model.UnrankedSearchContext;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.results.snippet.SnippetGenerator;
import nu.marginalia.index.reverse.query.IndexQuery;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/** Performs an index query */
public class IndexUnrankedQueryExecution {

    private static final Logger logger = LoggerFactory.getLogger(IndexUnrankedQueryExecution.class);

    private final int nodeId;
    private final DocumentDbReader documentDbReader;
    private final UnrankedSearchContext searchContext;
    private final String nodeName;

    private final CombinedIndexReader currentIndex;
    private final IndexResultRankingService rankingService;

    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;

    private final int limitTotal;

    private boolean finished = false;
    private long lastId = 0;

    public boolean isFinished() {
        return finished;
    }
    public long getLastId() {
        return lastId;
    }


    public IndexUnrankedQueryExecution(CombinedIndexReader currentIndex,
                                       DocumentDbReader documentDbReader,
                                       IndexResultRankingService rankingService,
                                       UnrankedSearchContext searchContext,
                                       int serviceNode)
    {
        this.currentIndex = currentIndex;
        this.documentDbReader = documentDbReader;
        this.searchContext = searchContext;
        this.nodeName = Integer.toString(serviceNode);
        this.nodeId = serviceNode;
        this.rankingService = rankingService;

        this.lastId = searchContext.afterCombinedDocId;
        budget = searchContext.budget;
        limitTotal = searchContext.limitTotal;

        queries = currentIndex.createUnrankedQueries(searchContext);
    }

    public List<RpcDecoratedResultItem> run() throws InterruptedException, IOException, SQLException {
        LongOpenHashSet seenDocIds = new LongOpenHashSet(limitTotal*2);
        List<RankableDocument> results = new ArrayList<>(limitTotal);
        List<RpcDecoratedResultItem> ret = new ArrayList<>(limitTotal);

        var buffer = new LongQueryBuffer(limitTotal);

        try {
            for (IndexQuery query : queries) {
                if (!budget.hasTimeLeft()) break;
                if (limitTotal <= seenDocIds.size()) break;

                while (query.hasMore() && budget.hasTimeLeft() && seenDocIds.size() < limitTotal) {
                    buffer.reset();
                    query.getMoreResults(buffer);

                    if (buffer.isEmpty()) continue;

                    while (buffer.hasMore() && seenDocIds.size() < limitTotal) {
                        long nextId = buffer.currentValue();

                        if (seenDocIds.add(UrlIdCodec.removeRank(nextId))) {
                            var result = new RankableDocument(nextId);

                            result.item = new SearchResultItem(nextId,
                                    nodeId,
                                    currentIndex.getDocumentMetadata(nextId),
                                    currentIndex.getHtmlFeatures(nextId),
                                    0, 0L);

                            results.add(result);
                        }

                        buffer.rejectAndAdvance(); // cheaper than retain fwiw
                    }
                }

                finished = !buffer.hasMore() && !query.hasMore();
            }
        }
        finally {
            buffer.dispose();
        }

        results.sort(Comparator.naturalOrder());

        if (!results.isEmpty()) {
            lastId = results.getLast().combinedDocumentId;
        }

        Map<Long, DocdbUrlDetail> detailsById = documentDbReader.getUrlDetails(new LongArrayList(seenDocIds));
        ResultConverter converter = new ResultConverter();

        try (SnippetGenerator snippetGenerator = new SnippetGenerator(currentIndex, searchContext)) {
            String[] leads = snippetGenerator.generate(results);

            for (int i = 0; i < results.size(); i++) {
                RankableDocument doc = results.get(i);

                final long id = doc.item.getDocumentId();
                final DocdbUrlDetail docData = detailsById.get(id);

                if (docData == null)
                    continue;

                int pubDate = currentIndex.getDocPubDate(doc.item.combinedId);

                converter.convert(doc, docData, leads[i], nodeId, pubDate).ifPresent(ret::add);
            }
        }

        return ret;
    }


    private static class ResultConverter {
        private final LongOpenHashSet seenDocumentHashes = new LongOpenHashSet();

        @Nullable
        public Optional<RpcDecoratedResultItem> convert(RankableDocument doc,
                                                 DocdbUrlDetail docData,
                                                 @Nullable String leadDescription,
                                                 int nodeId,
                                                 int pubDate) {
            SearchResultItem resultItem = doc.item;

            // Filter out duplicates by content
            if (!seenDocumentHashes.add(docData.dataHash())) {
                return Optional.empty();
            }

            var rawItem = RpcRawResultItem.newBuilder();

            rawItem.setCombinedId(resultItem.combinedId);
            rawItem.setHtmlFeatures(resultItem.htmlFeatures);
            rawItem.setEncodedDocMetadata(resultItem.encodedDocMetadata);
            rawItem.setHasPriorityTerms(resultItem.hasPrioTerm);
            rawItem.setNode(resultItem.nodeId);

            for (var score : resultItem.keywordScores) {
                rawItem.addKeywordScores(
                        RpcResultKeywordScore.newBuilder()
                                .setFlags(score.flags)
                                .setPositions(score.positionCount)
                                .setKeyword(score.keyword)
                );
            }

            var decoratedBuilder = RpcDecoratedResultItem.newBuilder()
                    .setDataHash(docData.dataHash())
                    .setDescription(Objects.requireNonNullElse(leadDescription, ""))
                    .setFeatures(docData.features())
                    .setFormat(docData.format())
                    .setRankingScore(resultItem.getScore())
                    .setTitle(docData.title())
                    .setUrl(docData.url().toString())
                    .setUrlQuality(docData.urlQuality())
                    .setWordsTotal(docData.wordsTotal())
                    .setBestPositions(resultItem.getBestPositions())
                    .setResultsFromDomain(doc.resultsFromDomain)
                    .setRawItem(rawItem);

            if (docData.pubYear() != null) {
                decoratedBuilder.setPubYear(docData.pubYear());
            }
            decoratedBuilder.setPubDate(pubDate);

            return Optional.of(decoratedBuilder.build());
        }

    }


}
