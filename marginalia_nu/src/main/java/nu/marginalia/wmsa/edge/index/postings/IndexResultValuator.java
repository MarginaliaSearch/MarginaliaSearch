package nu.marginalia.wmsa.edge.index.postings;

import gnu.trove.list.TLongList;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;

import java.util.List;
import java.util.Objects;

public class IndexResultValuator {
    private final IndexMetadataService metadataService;
    private final List<List<String>> searchTermVariants;
    private final int[] termIdsAll;

    private final TLongHashSet resultsWithPriorityTerms;

    private final TObjectIntHashMap<String> termToId = new TObjectIntHashMap<>(10, 0.75f, -1);
    private final TermMetadata termMetadata;

    public IndexResultValuator(SearchIndexControl indexes, TLongList results, List<EdgeSearchSubquery> subqueries) {
        this.metadataService = new IndexMetadataService(indexes);
        this.searchTermVariants = subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();

        var lexiconReader = Objects.requireNonNull(indexes.getLexiconReader());
        IntArrayList termIdsList = new IntArrayList();

        searchTermVariants.stream().flatMap(List::stream).distinct().forEach(term -> {
            int id = lexiconReader.get(term);

            if (id >= 0) {
                termIdsList.add(id);
                termToId.put(term, id);
            }
        });

        final long[] resultsArray = results.toArray();

        termIdsAll = termIdsList.toArray(new int[0]);
        termMetadata = new TermMetadata(resultsArray, termIdsAll);

        int[] priorityTermIds =
                subqueries.stream()
                        .flatMap(sq -> sq.searchTermsPriority.stream())
                        .distinct()
                        .mapToInt(lexiconReader::get)
                        .filter(id -> id >= 0)
                        .toArray();

        resultsWithPriorityTerms = new TLongHashSet(results.size());
        for (int priorityTerm : priorityTermIds) {
            long[] metadata = metadataService.getTermMetadata(priorityTerm, resultsArray);
            for (int i = 0; i < metadata.length; i++) {
                if (metadata[i] != 0) resultsWithPriorityTerms.add(resultsArray[i]);
            }
        }


    }

    public EdgeSearchResultItem evaluateResult(long id) {

        EdgeSearchResultItem searchResult = new EdgeSearchResultItem(id);
        final long urlIdInt = searchResult.getUrlIdInt();

        searchResult.setDomainId(metadataService.getDomainId(urlIdInt));

        long docMetadata = metadataService.getDocumentMetadata(urlIdInt);

        double bestScore = 0;
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

    private double evaluateSubquery(EdgeSearchResultItem searchResult,
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

            EdgeSearchResultKeywordScore score = new EdgeSearchResultKeywordScore(
                    querySetId,
                    searchTerm,
                    metadata,
                    docMetadata,
                    resultsWithPriorityTerms.contains(searchResult.combinedId)
            );
            searchResult.scores.add(score);

            setScore += score.termValue();

            if (termIdx == 0) {
                setScore += score.documentValue();
            }

            setSize++;
        }

        setScore += calculateTermCoherencePenalty(searchResult.getUrlIdInt(), termToId, termList);

        return setScore/setSize;
    }

    private double calculateTermCoherencePenalty(int urlId, TObjectIntHashMap<String> termToId, List<String> termList) {
        long maskDirectGenerous = ~0;
        long maskDirectRaw = ~0;
        long maskAdjacent = ~0;

        final int flagBitMask = EdgePageWordFlags.Title.asBit()
                              | EdgePageWordFlags.Subjects.asBit()
                              | EdgePageWordFlags.Synthetic.asBit();

        for (String term : termList) {
            var meta = termMetadata.getTermMetadata(termToId.get(term), urlId);
            long positions;

            if (meta == 0) {
                return 1000;
            }

            positions = EdgePageWordMetadata.decodePositions(meta);

            maskDirectRaw &= positions;

            if (positions != 0 && !EdgePageWordMetadata.hasAnyFlags(meta, flagBitMask)) {
                maskAdjacent &= (positions | (positions << 1) | (positions >>> 1));
                maskDirectGenerous &= positions;
            }

        }

        if (maskAdjacent == 0) {
            return 40;
        }

        if (maskDirectGenerous == 0) {
            return 20;
        }

        if (maskDirectRaw == 0) {
            return 2;
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
