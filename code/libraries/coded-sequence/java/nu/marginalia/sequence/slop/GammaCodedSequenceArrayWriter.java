package nu.marginalia.sequence.slop;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;
import java.util.List;

public interface GammaCodedSequenceArrayWriter extends AutoCloseable, ColumnWriter {
    void put(List<GammaCodedSequence> sequence) throws IOException;
    void close() throws IOException;
}
