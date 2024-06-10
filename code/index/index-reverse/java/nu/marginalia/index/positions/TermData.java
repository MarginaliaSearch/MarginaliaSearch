package nu.marginalia.index.positions;

import nu.marginalia.sequence.GammaCodedSequence;

import java.nio.ByteBuffer;

public class TermData {
    private final ByteBuffer buffer;

    public TermData(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public byte flags() {
        return buffer.get(0);
    }

    public GammaCodedSequence positions() {
        return new GammaCodedSequence(buffer, 1, buffer.capacity());
    }
}
