package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterBTreeRange;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepIf;

import java.util.*;
import java.util.stream.Collectors;

public class IndexDomainQueryFactory {
    SearchIndex baseIndex;
    List<SearchIndex> requiredIndices;

    public Collection<SearchIndex> getIndicies() {
        return requiredIndices;
    }

    public IndexDomainQueryFactory(SearchIndex baseIndex, List<SearchIndex> requiredIndices) {
        this.baseIndex = baseIndex;
        this.requiredIndices = requiredIndices.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    public IndexQuery buildQuery(IndexQueryCachePool cachePool, int firstWordId) {
        if (baseIndex == null) {
            return new IndexQuery(Collections.emptyList());
        }

        List<EntrySource> sources = new ArrayList<>(1);

        var range = baseIndex.rangeForWord(cachePool, firstWordId);
        if (range.isPresent()) {
            sources.add(range.asEntrySource());
        }

        var query = new IndexQuery(sources);
        for (var required : requiredIndices) {
            var requiredRange = required.rangeForWord(firstWordId);
            if (requiredRange.isPresent()) {
                query.addInclusionFilter(new QueryFilterBTreeRange(required, requiredRange, cachePool));
            }
            else {
                query.addInclusionFilter(QueryFilterStepIf.noPass());
            }
        }

        return query;
    }

}

