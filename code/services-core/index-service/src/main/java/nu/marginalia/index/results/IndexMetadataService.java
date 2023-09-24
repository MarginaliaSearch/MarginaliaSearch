package nu.marginalia.index.results;

import com.google.inject.Inject;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import gnu.trove.set.hash.TLongHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.svc.SearchTermsService;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.ranking.ResultValuator;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public class IndexMetadataService {
    private final SearchIndex index;
    private final SearchTermsService searchTermsService;
    private final ResultValuator searchResultValuator;

    @Inject
    public IndexMetadataService(SearchIndex index,
                                SearchTermsService searchTermsService,
                                ResultValuator searchResultValuator) {
        this.index = index;
        this.searchTermsService = searchTermsService;
        this.searchResultValuator = searchResultValuator;
    }

    public long getDocumentMetadata(long docId) {
        return index.getDocumentMetadata(docId);
    }

    public int getHtmlFeatures(long urlId) {
        return index.getHtmlFeatures(urlId);
    }

    public TermMetadataForDocuments getTermMetadataForDocuments(long[] docIdsAll, long[] termIdsList) {
        return new TermMetadataForDocuments(docIdsAll, termIdsList);
    }

    public QuerySearchTerms getSearchTerms(List<SearchSubquery> searchTermVariants) {

        LongArrayList termIdsList = new LongArrayList();

        TObjectLongHashMap<String> termToId = new TObjectLongHashMap<>(10, 0.75f, -1);

        for (var subquery : searchTermVariants) {
            for (var term : subquery.searchTermsInclude) {
                if (termToId.containsKey(term)) {
                    continue;
                }

                long id = searchTermsService.getWordId(term);
                termIdsList.add(id);
                termToId.put(term, id);
            }
        }

        return new QuerySearchTerms(termToId,
                termIdsList.toLongArray(),
                getTermCoherences(searchTermVariants));
    }


    private TermCoherences getTermCoherences(List<SearchSubquery> searchTermVariants) {
        List<long[]> coherences = new ArrayList<>();

        for (var subquery : searchTermVariants) {
            for (var coh : subquery.searchTermCoherences) {
                long[] ids = coh.stream().mapToLong(searchTermsService::getWordId).toArray();
                coherences.add(ids);
            }

            // It's assumed each subquery has identical coherences
            break;
        }

        return new TermCoherences(coherences);
    }

    public TLongHashSet getResultsWithPriorityTerms(List<SearchSubquery> subqueries, long[] resultsArray) {
        long[] priorityTermIds =
                subqueries.stream()
                        .flatMap(sq -> sq.searchTermsPriority.stream())
                        .distinct()
                        .mapToLong(searchTermsService::getWordId)
                        .toArray();

        var ret = new TLongHashSet(resultsArray.length);

        for (long priorityTerm : priorityTermIds) {
            long[] metadata = index.getTermMetadata(priorityTerm, resultsArray);
            for (int i = 0; i < metadata.length; i++) {
                if (metadata[i] != 0) ret.add(resultsArray[i]);
            }
        }

        return ret;
    }

    public ResultValuator getSearchResultValuator() {
        return searchResultValuator;
    }

    public class TermMetadataForDocuments {
        private final Long2ObjectArrayMap<Long2LongOpenHashMap> termdocToMeta;

        public TermMetadataForDocuments(long[] docIdsAll, long[] termIdsList) {
            termdocToMeta = new Long2ObjectArrayMap<>(termIdsList.length);

            for (long termId : termIdsList) {
                var metadata = index.getTermMetadata(termId, docIdsAll);
                termdocToMeta.put(termId, new Long2LongOpenHashMap(docIdsAll, metadata));
            }
        }

        public long getTermMetadata(long termId, long docId) {
            var docsForTerm = termdocToMeta.get(termId);
            if (docsForTerm == null) {
                return 0;
            }
            return docsForTerm.getOrDefault(docId, 0);
        }

        public boolean testCoherence(long docId, TermCoherences coherences) {

            for (var coherenceSet : coherences.words()) {
                long overlap = 0xFF_FFFF_FFFF_FFFFL;

                for (var word : coherenceSet) {
                    long positions = WordMetadata.decodePositions(getTermMetadata(word, docId));
                    overlap &= positions;
                }
                if (overlap == 0L) {
                    return false;
                }
            }

            return true;
        }
    }

    public static class QuerySearchTerms {
        private final TObjectLongHashMap<String> termToId;
        public final long[] termIdsAll;

        public final TermCoherences coherences;

        public QuerySearchTerms(TObjectLongHashMap<String> termToId,
                                long[] termIdsAll,
                                TermCoherences coherences) {
            this.termToId = termToId;
            this.termIdsAll = termIdsAll;
            this.coherences = coherences;
        }

        public long getIdForTerm(String searchTerm) {
            return termToId.get(searchTerm);
        }
    }

    /** wordIds that we require to be in the same sentence */
    public record TermCoherences(List<long[]> words) {}
}
