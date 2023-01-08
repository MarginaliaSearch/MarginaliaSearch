package nu.marginalia.wmsa.edge.index.postings.forward;

import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.index.query.IndexQueryParams;
import nu.marginalia.wmsa.edge.index.query.filter.QueryFilterStepIf;
import nu.marginalia.wmsa.edge.model.search.domain.SpecificationLimitType;

public class ParamMatchingQueryFilter implements QueryFilterStepIf {
    private final IndexQueryParams params;
    private final ForwardIndexReader forwardIndexReader;

    public ParamMatchingQueryFilter(IndexQueryParams params, ForwardIndexReader forwardIndexReader) {
        this.params = params;
        this.forwardIndexReader = forwardIndexReader;
    }

    @Override
    public boolean test(long docId) {
        var post = forwardIndexReader.docPost(docId);

        if (!validateDomain(post)) {
            return false;
        }

        if (!validateQuality(post)) {
            return false;
        }

        if (!validateYear(post)) {
            return false;
        }

        if (!validateSize(post)) {
            return false;
        }
        return true;
    }

    private boolean validateDomain(ForwardIndexReader.DocPost post) {
        return params.searchSet().contains(post.domainId());
    }

    private boolean validateQuality(ForwardIndexReader.DocPost post) {
        final var limit = params.qualityLimit();

        if (limit.type() == SpecificationLimitType.NONE) {
            return true;
        }

        final int quality = EdgePageDocumentsMetadata.decodeQuality(post.meta());

        return limit.test(quality);
    }
    private boolean validateYear(ForwardIndexReader.DocPost post) {
        if (params.year().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = EdgePageDocumentsMetadata.decodeYear(post.meta());

        return params.year().test(postVal);
    }

    private boolean validateSize(ForwardIndexReader.DocPost post) {
        if (params.size().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = EdgePageDocumentsMetadata.decodeSize(post.meta());

        return params.size().test(postVal);
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
