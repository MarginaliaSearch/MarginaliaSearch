package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.sequence.CodedSequence;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public class RankableDocument implements Comparable<RankableDocument> {
    public final long combinedDocumentId;

    public long[] termFlags;
    public BitSet priorityTermsPresent;

    public DecodableDocumentSpans documentSpans;
    public long[] positionOffsets;
    public CodedSequence[] positions;
    public SearchResultItem item;

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
