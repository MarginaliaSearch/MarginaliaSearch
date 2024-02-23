package nu.marginalia.converting.writer;

import java.io.IOException;

public interface ConverterBatchWritableIf {
    void write(ConverterBatchWriter writer) throws IOException;
    String id();
    void close() throws Exception;
}
