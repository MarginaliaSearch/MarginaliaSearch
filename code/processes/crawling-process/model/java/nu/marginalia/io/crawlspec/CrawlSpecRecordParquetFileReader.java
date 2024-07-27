package nu.marginalia.io.crawlspec;

import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class CrawlSpecRecordParquetFileReader {
    @NotNull
    public static Stream<CrawlSpecRecord> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(CrawlSpecRecord.newHydrator()));
    }

    public static int count(Path path) throws IOException {
        try (var stream = stream(path)) {
            // FIXME This can be done in a more performant way by using another hydrator that only reads a single field
            return (int) stream.count();
        }
    }

}
