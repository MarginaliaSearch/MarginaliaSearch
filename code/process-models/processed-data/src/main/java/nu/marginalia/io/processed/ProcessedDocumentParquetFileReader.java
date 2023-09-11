package nu.marginalia.io.processed;

import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import nu.marginalia.codec.processed.ProcessedDocumentDataHydrator;
import nu.marginalia.model.processed.ProcessedDocumentData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ProcessedDocumentParquetFileReader {

    @NotNull
    public static Stream<ProcessedDocumentData> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(new ProcessedDocumentDataHydrator()));
    }
}
