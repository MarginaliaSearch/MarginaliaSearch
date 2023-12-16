package nu.marginalia.crawling.parquet;

import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class CrawledDocumentParquetRecordFileReader {

    @NotNull
    public static Stream<CrawledDocumentParquetRecord> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(CrawledDocumentParquetRecord.newHydrator()));
    }

}
