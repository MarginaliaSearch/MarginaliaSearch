package nu.marginalia.keyword.model;

import nu.marginalia.sequence.CodedSequence;

import java.io.Serial;
import java.io.Serializable;

public final class DocumentKeywords implements Serializable {

    @Serial
    private static final long serialVersionUID = 1387282293082091432L;

    public final String[] keywords;
    public final long[] metadata;
    public final CodedSequence[] positions;

    public DocumentKeywords(String[] keywords,
                            long[] metadata,
                            CodedSequence[] positions)
    {
        this.keywords = keywords;
        this.metadata = metadata;
        this.positions = positions;

        assert keywords.length == metadata.length;
    }

    public boolean isEmpty() {
        return keywords.length == 0;
    }

    public int size() {
        return keywords.length;
    }

}


