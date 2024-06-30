package nu.marginalia.index.results;

import com.google.inject.Inject;
import gnu.trove.map.hash.TObjectLongHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.index.results.model.TermCoherenceGroupList;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.TermMetadataList;
import nu.marginalia.index.results.model.ids.TermIdList;

import java.lang.foreign.Arena;
import java.util.ArrayList;

import static nu.marginalia.index.results.model.TermCoherenceGroupList.TermCoherenceGroup;

public class IndexMetadataService {
    private final StatefulIndex statefulIndex;

    @Inject
    public IndexMetadataService(StatefulIndex index) {
        this.statefulIndex = index;
    }

    public Long2ObjectArrayMap<TermMetadataList>
        getTermMetadataForDocuments(Arena arena, CombinedDocIdList combinedIdsAll, TermIdList termIdsList)
    {
        var currentIndex = statefulIndex.get();

        Long2ObjectArrayMap<TermMetadataList> termdocToMeta =
                new Long2ObjectArrayMap<>(termIdsList.size());

        for (long termId : termIdsList.array()) {
            termdocToMeta.put(termId, currentIndex.getTermMetadata(arena, termId, combinedIdsAll));
        }

        return termdocToMeta;
    }

    public QuerySearchTerms getSearchTerms(CompiledQuery<String> compiledQuery, SearchQuery searchQuery) {

        LongArrayList termIdsList = new LongArrayList();
        LongArrayList termIdsPrio = new LongArrayList();

        TObjectLongHashMap<String> termToId = new TObjectLongHashMap<>(10, 0.75f, -1);

        for (String word : compiledQuery) {
            long id = SearchTermsUtil.getWordId(word);
            termIdsList.add(id);
            termToId.put(word, id);
        }

        for (var term : searchQuery.searchTermsAdvice) {
            if (termToId.containsKey(term)) {
                continue;
            }

            long id = SearchTermsUtil.getWordId(term);
            termIdsList.add(id);
            termToId.put(term, id);
        }

        for (var term : searchQuery.searchTermsPriority) {
            if (termToId.containsKey(term)) {
                long id = SearchTermsUtil.getWordId(term);
                termIdsPrio.add(id);
            }
            else {
                long id = SearchTermsUtil.getWordId(term);
                termIdsList.add(id);
                termIdsPrio.add(id);
                termToId.put(term, id);
            }
        }

        var idsAll = new TermIdList(termIdsList);
        var idsPrio = new TermIdList(termIdsPrio);

        var constraints = new ArrayList<TermCoherenceGroup>();
        for (var coherence : searchQuery.searchTermCoherences) {
            constraints.add(new TermCoherenceGroup(coherence, idsAll));
        }

        return new QuerySearchTerms(termToId,
                idsAll,
                idsPrio,
                new TermCoherenceGroupList(constraints)
        );
    }

}
