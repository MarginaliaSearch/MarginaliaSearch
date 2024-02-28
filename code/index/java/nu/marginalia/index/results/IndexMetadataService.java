package nu.marginalia.index.results;

import com.google.inject.Inject;
import gnu.trove.map.hash.TObjectLongHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.index.results.model.TermCoherenceGroupList;
import nu.marginalia.index.results.model.TermMetadataForCombinedDocumentIds;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.TermIdList;

import java.util.ArrayList;
import java.util.List;

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

    public QuerySearchTerms getSearchTerms(List<SearchSubquery> searchTermVariants) {

        LongArrayList termIdsList = new LongArrayList();

        TObjectLongHashMap<String> termToId = new TObjectLongHashMap<>(10, 0.75f, -1);

        for (var subquery : searchTermVariants) {
            for (var term : subquery.searchTermsInclude) {
                if (termToId.containsKey(term)) {
                    continue;
                }

                long id = SearchTermsUtil.getWordId(term);
                termIdsList.add(id);
                termToId.put(term, id);
            }
        }

        return new QuerySearchTerms(termToId,
                new TermIdList(termIdsList),
                getTermCoherences(searchTermVariants));
    }


    private TermCoherenceGroupList getTermCoherences(List<SearchSubquery> searchTermVariants) {
        List<TermCoherenceGroup> coherences = new ArrayList<>();

        for (var subquery : searchTermVariants) {
            for (var coh : subquery.searchTermCoherences) {
                coherences.add(new TermCoherenceGroup(coh));
            }

            // It's assumed each subquery has identical coherences
            break;
        }

        return new TermCoherenceGroupList(coherences);
    }

}
