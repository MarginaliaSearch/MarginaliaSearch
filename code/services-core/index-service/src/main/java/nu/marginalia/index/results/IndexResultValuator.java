package nu.marginalia.index.results;

import gnu.trove.list.TLongList;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import nu.marginalia.index.svc.SearchTermsService;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.query.IndexQueryParams;

import java.util.List;
import java.util.OptionalInt;

public class IndexResultValuator {
    private final IndexMetadataService metadataService;
    private final List<List<String>> searchTermVariants;
    private final IndexQueryParams queryParams;
    private final int[] termIdsAll;

    private final TLongHashSet resultsWithPriorityTerms;

    private final TObjectIntHashMap<String> termToId = new TObjectIntHashMap<>(10, 0.75f, -1);
    private final TermMetadata termMetadata;

    public IndexResultValuator(SearchTermsService searchTermsSvc,
                               IndexMetadataService metadataService,
                               TLongList results,
                               List<SearchSubquery> subqueries,
                               IndexQueryParams queryParams) {
        this.searchTermVariants = subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();
        this.queryParams = queryParams;
        this.metadataService = metadataService;

        IntArrayList termIdsList = new IntArrayList();

        searchTermVariants.stream().flatMap(List::stream).distinct().forEach(term -> {
            searchTermsSvc.lookUpWord(term).ifPresent(id -> {
                termIdsList.add(id);
                termToId.put(term, id);
            });
        });

        final long[] resultsArray = results.toArray();

        termIdsAll = termIdsList.toArray(new int[0]);
        termMetadata = new TermMetadata(resultsArray, termIdsAll);

        int[] priorityTermIds =
                subqueries.stream()
                        .flatMap(sq -> sq.searchTermsPriority.stream())
                        .distinct()
                        .map(searchTermsSvc::lookUpWord)
                        .filter(OptionalInt::isPresent)
                        .mapToInt(OptionalInt::getAsInt)
                        .toArray();

        resultsWithPriorityTerms = new TLongHashSet(results.size());
        for (int priorityTerm : priorityTermIds) {
            long[] metadata = metadataService.getTermMetadata(priorityTerm, resultsArray);
            for (int i = 0; i < metadata.length; i++) {
                if (metadata[i] != 0) resultsWithPriorityTerms.add(resultsArray[i]);
            }
        }


    }

    public SearchResultItem evaluateResult(long id) {

        SearchResultItem searchResult = new SearchResultItem(id);
        final long urlIdInt = searchResult.getUrlIdInt();

        searchResult.setDomainId(metadataService.getDomainId(urlIdInt));

        long docMetadata = metadataService.getDocumentMetadata(urlIdInt);

        double bestScore = 1000;
        for (int querySetId = 0; querySetId < searchTermVariants.size(); querySetId++) {
            bestScore = Math.min(bestScore,
                    evaluateSubquery(searchResult,
                            docMetadata,
                            querySetId,
                            searchTermVariants.get(querySetId))
            );
        }

        if (resultsWithPriorityTerms.contains(id)) {
            bestScore -= 50;
        }

        searchResult.setScore(bestScore);

        return searchResult;
    }

    private double evaluateSubquery(SearchResultItem searchResult,
                                    long docMetadata,
                                    int querySetId,
                                    List<String> termList)
    {
        double setScore = 0;
        int setSize = 0;

        for (int termIdx = 0; termIdx < termList.size(); termIdx++) {
            String searchTerm = termList.get(termIdx);

            final int termId = termToId.get(searchTerm);

            long metadata = termMetadata.getTermMetadata(termId, searchResult.getUrlIdInt());

            SearchResultKeywordScore score = new SearchResultKeywordScore(
                    querySetId,
                    searchTerm,
                    metadata,
                    docMetadata,
                    resultsWithPriorityTerms.contains(searchResult.combinedId)
            );

            searchResult.scores.add(score);

            setScore += score.termValue();

            if (!filterRequired(metadata, queryParams.queryStrategy())) {
                return 1000;
            }

            if (termIdx == 0) {
                setScore += score.documentValue();
            }

            setSize++;
        }

        setScore += calculateTermCoherencePenalty(searchResult.getUrlIdInt(), termToId, termList);

        return setScore/setSize;
    }

    private boolean filterRequired(long metadata, QueryStrategy queryStrategy) {
        if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SITE) {
            return WordFlags.Site.isPresent(metadata);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SUBJECT) {
            return WordFlags.Subjects.isPresent(metadata);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_TITLE) {
            return WordFlags.Title.isPresent(metadata);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_URL) {
            return WordFlags.UrlPath.isPresent(metadata);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_DOMAIN) {
            return WordFlags.UrlDomain.isPresent(metadata);
        }
        return true;
    }

    private double calculateTermCoherencePenalty(int urlId, TObjectIntHashMap<String> termToId, List<String> termList) {
        long maskDirectGenerous = ~0;
        long maskDirectRaw = ~0;
        long maskAdjacent = ~0;

        final int flagBitMask = WordFlags.Title.asBit()
                              | WordFlags.Subjects.asBit()
                              | WordFlags.Synthetic.asBit();

        int termCount = 0;
        double tfIdfSum = 1.;

        for (String term : termList) {
            var meta = termMetadata.getTermMetadata(termToId.get(term), urlId);
            long positions;

            if (meta == 0) {
                return 1000;
            }

            positions = WordMetadata.decodePositions(meta);

            maskDirectRaw &= positions;

            if (positions != 0 && !WordMetadata.hasAnyFlags(meta, flagBitMask)) {
                maskAdjacent &= (positions | (positions << 1) | (positions >>> 1));
                maskDirectGenerous &= positions;
            }

            termCount++;
            tfIdfSum += WordMetadata.decodeTfidf(meta);
        }

        double avgTfIdf = termCount / tfIdfSum;

        if (maskAdjacent == 0) {
            return Math.min(5, Math.max(-2, 40 - 0.5 * avgTfIdf));
        }

        if (maskDirectGenerous == 0) {
            return Math.min(5, Math.max(-1, 20 - 0.3 *  avgTfIdf));
        }

        if (maskDirectRaw == 0) {
            return Math.min(5, Math.max(-1, 15 - 0.2 *  avgTfIdf));
        }

        return Long.numberOfTrailingZeros(maskDirectGenerous)/5. - Long.bitCount(maskDirectGenerous);
    }


    class TermMetadata {
        private final Long2LongOpenHashMap termdocToMeta;

        public TermMetadata(long[] docIdsAll, int[] termIdsList) {
            termdocToMeta = new Long2LongOpenHashMap(docIdsAll.length * termIdsAll.length, 0.5f);

            for (int term : termIdsList) {
                var metadata = metadataService.getTermMetadata(term, docIdsAll);
                for (int i = 0; i < docIdsAll.length; i++) {
                    termdocToMeta.put(termdocKey(term, docIdsAll[i]), metadata[i]);
                }
            }

        }

        public long getTermMetadata(int termId, long docId) {
            return termdocToMeta.getOrDefault(termdocKey(termId, docId), 0);
        }
    }

    private long termdocKey(int termId, long docId) {
        return (docId << 32) | termId;
    }

}
