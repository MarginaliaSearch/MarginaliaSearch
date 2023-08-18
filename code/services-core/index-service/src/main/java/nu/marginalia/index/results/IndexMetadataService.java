package nu.marginalia.index.results;

import com.google.inject.Inject;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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

    public long getDocumentMetadata(long urlId) {
        return index.getDocumentMetadata(urlId);
    }

    public int getHtmlFeatures(long urlId) {
        return index.getHtmlFeatures(urlId);
    }

    public int getDomainId(long urlId) {
        return index.getDomainId(urlId);
    }

    public long[] getTermMetadata(int termId, long[] docIdsAll) {
        return index.getTermMetadata(termId, docIdsAll);
    }

    public TermMetadata getTermMetadata(long[] docIdsAll, int[] termIdsList) {
        var termdocToMeta = new Long2LongOpenHashMap(docIdsAll.length * termIdsList.length, 0.5f);

        for (int term : termIdsList) {
            var metadata = getTermMetadata(term, docIdsAll);

            for (int i = 0; i < docIdsAll.length; i++) {
                termdocToMeta.put(termdocKey(term, docIdsAll[i]), metadata[i]);
            }
        }

        return new TermMetadata(termdocToMeta);
    }

    public QuerySearchTerms getSearchTerms(List<SearchSubquery> searchTermVariants) {

        IntArrayList termIdsList = new IntArrayList();

        TObjectIntHashMap<String> termToId = new TObjectIntHashMap<>(10, 0.75f, -1);

        for (var subquery : searchTermVariants) {
            for (var term : subquery.searchTermsInclude) {
                if (termToId.containsKey(term)) {
                    continue;
                }

                var id = searchTermsService.lookUpWord(term);
                if (id.isPresent()) {
                    termIdsList.add(id.getAsInt());
                    termToId.put(term, id.getAsInt());
                }
            }
        }


        return new QuerySearchTerms(termToId,
                termIdsList.toIntArray(),
                getTermCoherences(searchTermVariants));
    }


    private TermCoherences getTermCoherences(List<SearchSubquery> searchTermVariants) {
        List<int[]> coherences = new ArrayList<>();

        for (var subquery : searchTermVariants) {
            for (var coh : subquery.searchTermCoherences) {
                int[] ids = coh.stream().map(searchTermsService::lookUpWord).filter(OptionalInt::isPresent).mapToInt(OptionalInt::getAsInt).toArray();
                coherences.add(ids);
            }

            // It's assumed each subquery has identical coherences
            break;
        }

        return new TermCoherences(coherences);
    }

    public TLongHashSet getResultsWithPriorityTerms(List<SearchSubquery> subqueries, long[] resultsArray) {
        int[] priorityTermIds =
                subqueries.stream()
                        .flatMap(sq -> sq.searchTermsPriority.stream())
                        .distinct()
                        .map(searchTermsService::lookUpWord)
                        .filter(OptionalInt::isPresent)
                        .mapToInt(OptionalInt::getAsInt)
                        .toArray();

        var ret = new TLongHashSet(resultsArray.length);

        for (int priorityTerm : priorityTermIds) {
            long[] metadata = getTermMetadata(priorityTerm, resultsArray);
            for (int i = 0; i < metadata.length; i++) {
                if (metadata[i] != 0) ret.add(resultsArray[i]);
            }
        }

        return ret;


    }

    public ResultValuator getSearchResultValuator() {
        return searchResultValuator;
    }

    public static class TermMetadata {
        private final Long2LongOpenHashMap termdocToMeta;

        public TermMetadata(Long2LongOpenHashMap termdocToMeta) {
            this.termdocToMeta = termdocToMeta;
        }

        public long getTermMetadata(int termId, long docId) {
            return termdocToMeta.getOrDefault(termdocKey(termId, docId), 0);
        }

        public boolean testCoherence(long docId, TermCoherences coherences) {

            for (var coherenceSet : coherences.words()) {
                long overlap = 0xFF_FFFF_FFFF_FFFFL;
                for (var word : coherenceSet) {
                    overlap &= WordMetadata.decodePositions(getTermMetadata(word, docId));
                }
                if (overlap == 0L) {
                    return false;
                }
            }

            return true;
        }
    }

    public static class QuerySearchTerms {
        private final TObjectIntHashMap<String> termToId;
        public final int[] termIdsAll;

        public final TermCoherences coherences;

        public QuerySearchTerms(TObjectIntHashMap<String> termToId, int[] termIdsAll, TermCoherences coherences) {
            this.termToId = termToId;
            this.termIdsAll = termIdsAll;
            this.coherences = coherences;
        }

        public int get(String searchTerm) {
            return termToId.get(searchTerm);
        }
    }

    public record TermCoherences(List<int[]> words) {}

    private static long termdocKey(int termId, long docId) {
        return (docId << 32) | Integer.toUnsignedLong(termId);
    }
}
