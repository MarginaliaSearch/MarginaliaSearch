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
import nu.marginalia.index.results.model.TermMetadataForCombinedDocumentIds;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.TermIdList;

import static nu.marginalia.index.results.model.TermCoherenceGroupList.TermCoherenceGroup;
import static nu.marginalia.index.results.model.TermMetadataForCombinedDocumentIds.DocumentsWithMetadata;

public class IndexMetadataService {
    private final StatefulIndex index;

    @Inject
    public IndexMetadataService(StatefulIndex index) {
        this.index = index;
    }

    public TermMetadataForCombinedDocumentIds getTermMetadataForDocuments(CombinedDocIdList combinedIdsAll,
                                                                          TermIdList termIdsList)
    {
        Long2ObjectArrayMap<DocumentsWithMetadata> termdocToMeta =
                new Long2ObjectArrayMap<>(termIdsList.size());

        for (long termId : termIdsList.array()) {
            var metadata = index.getTermMetadata(termId, combinedIdsAll);
            termdocToMeta.put(termId,
                    new DocumentsWithMetadata(combinedIdsAll, metadata));
        }

        return new TermMetadataForCombinedDocumentIds(termdocToMeta);
    }

    public QuerySearchTerms getSearchTerms(CompiledQuery<String> compiledQuery, SearchQuery searchQuery) {

        LongArrayList termIdsList = new LongArrayList();

        TObjectLongHashMap<String> termToId = new TObjectLongHashMap<>(10, 0.75f, -1);

        for (String word : compiledQuery) {
            long id = SearchTermsUtil.getWordId(word);
            termIdsList.add(id);
            termToId.put(word, id);
        }

        return new QuerySearchTerms(termToId,
                new TermIdList(termIdsList),
                new TermCoherenceGroupList(
                        searchQuery.searchTermCoherences.stream().map(TermCoherenceGroup::new).toList()
                )
        );
    }

}
