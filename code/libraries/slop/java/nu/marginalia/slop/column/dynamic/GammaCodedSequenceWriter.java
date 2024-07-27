package nu.marginalia.slop.column.dynamic;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface GammaCodedSequenceWriter extends AutoCloseable, ColumnWriter {
    void put(GammaCodedSequence sequence) throws IOException;
    void close() throws IOException;
}
