package nu.marginalia.model.idx;

import nu.marginalia.sequence.VarintCodedSequence;

public record CodedWordSpan(byte code, VarintCodedSequence spans) {
}
