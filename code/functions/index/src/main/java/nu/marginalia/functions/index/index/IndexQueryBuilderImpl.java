package nu.marginalia.functions.index.index;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.functions.index.model.IndexQueryParams;
import nu.marginalia.index.ReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;

public class IndexQueryBuilderImpl implements IndexQueryBuilder  {
    private final IndexQuery query;
    private final ReverseIndexReader reverseIndexFullReader;
    private final ReverseIndexReader reverseIndexPrioReader;

    /* Keep track of already added include terms to avoid redundant checks.
     *
     * Warning: This may cause unexpected behavior if for example attempting to
     * first check one index and then another for the same term. At the moment, that
     * makes no sense, but in the future, that might be a thing one might want to do.
     * */
    private final TLongHashSet alreadyConsideredTerms = new TLongHashSet();

    IndexQueryBuilderImpl(ReverseIndexReader reverseIndexFullReader,
                          ReverseIndexReader reverseIndexPrioReader,
                          IndexQuery query, long... sourceTerms)
    {
        this.query = query;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPrioReader = reverseIndexPrioReader;

        alreadyConsideredTerms.addAll(sourceTerms);
    }

    public IndexQueryBuilder alsoFull(long termId) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(reverseIndexFullReader.also(termId));
        }

        return this;
    }

    public IndexQueryBuilder alsoPrio(long termId) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(reverseIndexPrioReader.also(termId));
        }

        return this;
    }

    public IndexQueryBuilder notFull(long termId) {

        query.addInclusionFilter(reverseIndexFullReader.not(termId));

        return this;
    }

    public IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep) {

        query.addInclusionFilter(filterStep);

        return this;
    }

    public IndexQuery build() {
        return query;
    }

    public static class ParamMatchingQueryFilter implements QueryFilterStepIf {
        private final IndexQueryParams params;
        private final ForwardIndexReader forwardIndexReader;

        public ParamMatchingQueryFilter(IndexQueryParams params,
                                        ForwardIndexReader forwardIndexReader)
        {
            this.params = params;
            this.forwardIndexReader = forwardIndexReader;
        }

        @Override
        public boolean test(long combinedId) {
            long docId = UrlIdCodec.removeRank(combinedId);
            int domainId = UrlIdCodec.getDomainId(docId);

            long meta = forwardIndexReader.getDocMeta(docId);

            if (!validateDomain(domainId, meta)) {
                return false;
            }

            if (!validateQuality(meta)) {
                return false;
            }

            if (!validateYear(meta)) {
                return false;
            }

            if (!validateSize(meta)) {
                return false;
            }

            if (!validateRank(meta)) {
                return false;
            }

            return true;
        }

        private boolean validateDomain(int domainId, long meta) {
            return params.searchSet().contains(domainId, meta);
        }

        private boolean validateQuality(long meta) {
            final var limit = params.qualityLimit();

            if (limit.type() == SpecificationLimitType.NONE) {
                return true;
            }

            final int quality = DocumentMetadata.decodeQuality(meta);

            return limit.test(quality);
        }

        private boolean validateYear(long meta) {
            if (params.year().type() == SpecificationLimitType.NONE)
                return true;

            int postVal = DocumentMetadata.decodeYear(meta);

            return params.year().test(postVal);
        }

        private boolean validateSize(long meta) {
            if (params.size().type() == SpecificationLimitType.NONE)
                return true;

            int postVal = DocumentMetadata.decodeSize(meta);

            return params.size().test(postVal);
        }

        private boolean validateRank(long meta) {
            if (params.rank().type() == SpecificationLimitType.NONE)
                return true;

            int postVal = DocumentMetadata.decodeRank(meta);

            return params.rank().test(postVal);
        }

        @Override
        public double cost() {
            return 32;
        }

        @Override
        public String describe() {
            return getClass().getSimpleName();
        }
    }
}
