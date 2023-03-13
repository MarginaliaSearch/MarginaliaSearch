package nu.marginalia.index.forward;

import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.filter.QueryFilterStepIf;

public class ParamMatchingQueryFilter implements QueryFilterStepIf {
    private final IndexQueryParams params;
    private final ForwardIndexReader forwardIndexReader;

    public ParamMatchingQueryFilter(IndexQueryParams params, ForwardIndexReader forwardIndexReader) {
        this.params = params;
        this.forwardIndexReader = forwardIndexReader;
    }

    @Override
    public boolean test(long docId) {
        int urlId = (int) (docId & 0xFFFF_FFFFL);
        int domainId = forwardIndexReader.getDomainId(urlId);
        long meta = forwardIndexReader.getDocMeta(urlId);

        if (!validateDomain(domainId)) {
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

    private boolean validateDomain(int domainId) {
        return params.searchSet().contains(domainId);
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
