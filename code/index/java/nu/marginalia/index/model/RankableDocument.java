package nu.marginalia.index.model;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.sequence.CodedSequence;
import org.jetbrains.annotations.NotNull;

public class RankableDocument implements Comparable<RankableDocument> {
    public final long combinedDocumentId;

    public long[] termFlags;
    public boolean[] priorityTermsPresent;

    public volatile DecodableDocumentSpans documentSpans;
    public volatile long[] positionOffsets;
    public volatile CodedSequence[] positions;
    public volatile SearchResultItem item;

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
