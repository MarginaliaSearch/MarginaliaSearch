package nu.marginalia.index.model;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.sequence.CodedSequence;
import org.jetbrains.annotations.NotNull;

public class RankableDocument implements Comparable<RankableDocument> {
    public final long combinedDocumentId;

    public long[] termFlags;
    public boolean[] priorityTermsPresent;

    public long[] positionOffsets;

    public DocumentSpans documentSpans;
    public IntList[] positions;

    public SearchResultItem item;
    public int resultsFromDomain;

    public int domainId() {
        return UrlIdCodec.getDomainId(combinedDocumentId);
    }

    public RankableDocument(long combinedDocumentId) {
        this.combinedDocumentId = combinedDocumentId;
    }

    @Override
    public int compareTo(@NotNull RankableDocument document) {
        int test = Boolean.compare(item == null, document.item == null);

        if (test != 0)
            return test;

        if (item == null)
            return 1;

        return item.compareTo(document.item);
    }
}
