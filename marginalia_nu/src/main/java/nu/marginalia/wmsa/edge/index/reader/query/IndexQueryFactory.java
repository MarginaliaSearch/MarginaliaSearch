package nu.marginalia.wmsa.edge.index.reader.query;

import nu.marginalia.wmsa.edge.index.reader.IndexQueryCachePool;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import nu.marginalia.wmsa.edge.index.reader.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.reader.query.types.QueryFilterStep;
import nu.marginalia.wmsa.edge.index.reader.query.types.QueryFilterStepFromPredicate;
import nu.marginalia.wmsa.edge.index.reader.query.types.UrlRangeSubFilter;

import java.util.*;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

public class IndexQueryFactory {
    private final List<SearchIndex> requiredIndices;
    private final List<SearchIndex> excludeIndex;
    private final List<SearchIndex> priortyIndices;

    public Collection<SearchIndex> getIndicies() {
        return requiredIndices;
    }

    public IndexQueryFactory(List<SearchIndex> requiredIndices, List<SearchIndex> excludeIndex, List<SearchIndex> priortyIndices) {
        this.requiredIndices = requiredIndices.stream().filter(Objects::nonNull).collect(Collectors.toList());
        this.excludeIndex = excludeIndex;
        this.priortyIndices = priortyIndices;
    }

    public IndexQueryBuilder buildQuery(IndexQueryCachePool cachePool, int firstWordId) {
        List<EntrySource> sources = new ArrayList<>(requiredIndices.size());

        for (var ri : requiredIndices) {
            var range = ri.rangeForWord(cachePool, firstWordId);
            if (range.isPresent()) {
                sources.add(range.asEntrySource());
            }
        }


        IndexQuery query = new IndexQuery(sources);

        return new IndexQueryBuilder(query, cachePool);
    }

    public class IndexQueryBuilder {
        private final IndexQuery query;
        private final IndexQueryCachePool cachePool;

        IndexQueryBuilder(IndexQuery query,
                          IndexQueryCachePool cachePool) {
            this.query = query;
            this.cachePool = cachePool;
        }

        public void filter(LongPredicate predicate) {
            query.addInclusionFilter(new QueryFilterStepFromPredicate(predicate));
        }

        public IndexQueryBuilder also(int termId) {
            List<QueryFilterStep> filters = new ArrayList<>(requiredIndices.size());

            for (var ri : requiredIndices) {
                var range = ri.rangeForWord(cachePool, termId);

                if (range.isPresent()) {
                    filters.add(new UrlRangeSubFilter(ri, range, cachePool));
                }
                else {
                    filters.add(QueryFilterStep.noPass());
                }
            }

            filters.sort(Comparator.naturalOrder());
            query.addInclusionFilter(QueryFilterStep.anyOf(filters));

            return this;
        }


        public IndexQueryBuilder not(int termId) {
            for (var ri : excludeIndex) {
                var range = ri.rangeForWord(cachePool, termId);
                if (range.isPresent()) {
                    query.addInclusionFilter(range.asExcludeFilterStep(cachePool));
                }
            }

            return this;
        }

        public void prioritize(int termId) {
            for (var idx : priortyIndices) {
                var range = idx.rangeForWord(cachePool, termId);
                if (range.isPresent()) {
                    query.addPriorityFilter(new UrlRangeSubFilter(idx, range, cachePool));
                }
            }
        }

        public IndexQuery build() {
            return query;
        }

    }

}

