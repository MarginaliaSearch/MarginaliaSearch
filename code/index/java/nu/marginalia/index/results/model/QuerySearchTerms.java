package nu.marginalia.index.results.model;

import gnu.trove.map.hash.TObjectLongHashMap;
import nu.marginalia.index.results.model.ids.TermIdList;

public class QuerySearchTerms {
    private final TObjectLongHashMap<String> termToId;
    public final TermIdList termIdsAll;

    public final PhraseConstraintGroupList phraseConstraints;

    public QuerySearchTerms(TObjectLongHashMap<String> termToId,
                            TermIdList termIdsAll,
                            PhraseConstraintGroupList phraseConstraints) {
        this.termToId = termToId;
        this.termIdsAll = termIdsAll;
        this.phraseConstraints = phraseConstraints;
    }

    public long getIdForTerm(String searchTerm) {
        return termToId.get(searchTerm);
    }
}
