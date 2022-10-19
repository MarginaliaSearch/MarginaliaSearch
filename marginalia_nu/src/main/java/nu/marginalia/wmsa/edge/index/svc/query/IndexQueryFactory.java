package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import nu.marginalia.wmsa.edge.index.svc.query.types.EmptyEntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterBTreeRangeReject;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterBTreeRangeRetain;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepIf;

import java.util.*;
import java.util.stream.Collectors;

public class IndexQueryFactory {
    private final List<SearchIndex> requiredIndices;
    private final List<SearchIndex> excludeIndex;

    public Collection<SearchIndex> getIndicies() {
        return requiredIndices;
    }

    public IndexQueryFactory(List<SearchIndex> requiredIndices, List<SearchIndex> excludeIndex) {
        this.requiredIndices = requiredIndices.stream().filter(Objects::nonNull).collect(Collectors.toList());
        this.excludeIndex = excludeIndex;
    }

    public IndexQueryBuilder buildQuery(int firstWordId) {
        List<EntrySource> sources = new ArrayList<>(requiredIndices.size());

        for (var ri : requiredIndices) {
            var range = ri.rangeForWord(firstWordId);
            if (range.isPresent()) {
                sources.add(range.asEntrySource());
            }
        }

        return new IndexQueryBuilder(new IndexQuery(sources));
    }

    public IndexQueryBuilder buildQuery(int quality, int wordId) {
        List<EntrySource> sources = new ArrayList<>(requiredIndices.size());

        for (var ri : requiredIndices) {
            var range = ri.rangeForWord(wordId);
            if (range.isPresent()) {
                sources.add(range.asQualityLimitingEntrySource(quality));
            }
        }

        return new IndexQueryBuilder(new IndexQuery(sources));
    }

    public IndexQueryBuilder buildQuery(List<Integer> domains, int wordId) {
        List<EntrySource> sources = new ArrayList<>(requiredIndices.size());

        for (var ri : requiredIndices) {
            var range = ri.rangeForWord(wordId);

            if (range.isPresent()) {
                for (int dom : domains) {
                    long prefix = (long) dom << 32L;
                    long prefixNext = prefix + 0x0000_0001_0000_0000L;

                    var source = range.asPrefixSource(prefix, prefixNext);
                    if (source.hasMore()) {
                        sources.add(source);
                    }
                }
            }

        }

        if (sources.isEmpty()) {
            sources.add(new EmptyEntrySource());
        }

        return new IndexQueryBuilder(new IndexQuery(sources));
    }

    public class IndexQueryBuilder {
        private final IndexQuery query;

        IndexQueryBuilder(IndexQuery query) {
            this.query = query;
        }

        public IndexQueryBuilder also(int termId) {
            List<QueryFilterStepIf> filters = new ArrayList<>(requiredIndices.size());

            for (var ri : requiredIndices) {
                var range = ri.rangeForWord(termId);

                if (range.isPresent()) {
                    filters.add(new QueryFilterBTreeRangeRetain(range));
                }
            }
            if (filters.isEmpty()) {
                filters.add(QueryFilterStepIf.noPass());
            }


            if (filters.size() > 1) {
                filters.sort(Comparator.naturalOrder());
                query.addInclusionFilter(QueryFilterStepIf.anyOf(filters));
            }
            else {
                query.addInclusionFilter(filters.get(0));
            }

            return this;
        }

        public void addInclusionFilter(QueryFilterStepIf filter) {
            query.addInclusionFilter(filter);
        }

        public IndexQueryBuilder not(int termId) {
            for (var ri : excludeIndex) {
                var range = ri.rangeForWord(termId);
                if (range.isPresent()) {
                    query.addInclusionFilter(new QueryFilterBTreeRangeReject(range));
                }
            }

            return this;
        }

        public IndexQuery build() {
            return query;
        }

    }

}

