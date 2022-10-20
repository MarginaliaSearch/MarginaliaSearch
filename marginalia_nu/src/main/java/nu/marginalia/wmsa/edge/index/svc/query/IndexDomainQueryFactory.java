package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IndexDomainQueryFactory {
    SearchIndex baseIndex;

    public IndexDomainQueryFactory(SearchIndex sourceIndex) {
        this.baseIndex = sourceIndex;
    }

    public IndexQuery buildQuery(int firstWordId) {
        if (baseIndex == null) {
            return new IndexQuery(Collections.emptyList());
        }

        List<EntrySource> sources = new ArrayList<>(1);

        var range = baseIndex.rangeForWord(firstWordId);
        if (range.isPresent()) {
            sources.add(range.asDomainEntrySource());
        }

        return new IndexQuery(sources);
    }

}

