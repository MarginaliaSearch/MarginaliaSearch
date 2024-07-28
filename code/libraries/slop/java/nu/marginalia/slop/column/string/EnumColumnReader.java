package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;
import java.util.List;

public interface EnumColumnReader extends StringColumnReader, ColumnReader, AutoCloseable {

    List<String> getDictionary() throws IOException;
    int getOrdinal() throws IOException;

    String get() throws IOException;

    @Override
    long position() throws IOException;

    @Override
    void skip(long positions) throws IOException;

    @Override
    boolean hasRemaining() throws IOException;

    @Override
    void close() throws IOException;
}
