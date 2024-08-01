package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.ObjectColumnWriter;

import java.io.IOException;

public interface StringColumnWriter extends ObjectColumnWriter<String>, AutoCloseable {
    void put(String value) throws IOException;

    @Override
    void close() throws IOException;
}
