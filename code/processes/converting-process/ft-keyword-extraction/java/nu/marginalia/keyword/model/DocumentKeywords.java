package nu.marginalia.keyword.model;

import nu.marginalia.model.idx.CodedWordSpan;
import nu.marginalia.sequence.VarintCodedSequence;

import java.util.List;

public final class DocumentKeywords {

    public final List<String> keywords;
    public final byte[] metadata;
    public final List<VarintCodedSequence> positions;
    public final List<CodedWordSpan> spans;

    public DocumentKeywords(List<String> keywords,
                            byte[] metadata,
                            List<VarintCodedSequence> positions,
                            List<CodedWordSpan> spans)
    {
        this.keywords = keywords;
        this.metadata = metadata;
        this.positions = positions;
        this.spans = spans;

        assert keywords.size() == metadata.length;
    }

    public boolean isEmpty() {
        return keywords.isEmpty();
    }

    public int size() {
        return keywords.size();
    }

}


