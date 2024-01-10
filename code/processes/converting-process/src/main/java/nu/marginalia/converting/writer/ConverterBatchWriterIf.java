package nu.marginalia.converting.writer;

import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;

import java.io.IOException;

public interface ConverterBatchWriterIf {

    void write(ConverterBatchWritableIf writable) throws IOException;

    void writeSideloadSource(SideloadSource sideloadSource) throws IOException;

    void writeProcessedDomain(ProcessedDomain domain);
}
