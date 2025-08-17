package nu.marginalia.keyword.model;

import nu.marginalia.sequence.VarintCodedSequence;

import java.util.List;

public record DocumentKeywords(List<String> keywords,
                               byte[] metadata,
                               List<VarintCodedSequence> positions,
                               byte[] spanCodes,
                               List<VarintCodedSequence> spanSequences) {

    public boolean isEmpty() {
        return keywords.isEmpty();
    }

    public int size() {
        return keywords.size();
    }

}


