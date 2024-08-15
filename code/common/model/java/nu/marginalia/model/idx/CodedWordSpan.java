package nu.marginalia.model.idx;

import nu.marginalia.sequence.GammaCodedSequence;

public record CodedWordSpan(byte code, GammaCodedSequence spans) {
}
