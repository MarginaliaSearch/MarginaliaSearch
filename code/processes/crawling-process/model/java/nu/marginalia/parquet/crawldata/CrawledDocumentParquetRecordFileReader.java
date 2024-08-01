package nu.marginalia.parquet.crawldata;

import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class CrawledDocumentParquetRecordFileReader {

    @NotNull
    public static Stream<CrawledDocumentParquetRecord> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(CrawledDocumentParquetRecord.newHydrator()));
    }

    /** Count the number of documents with a 200 status code */
    public static int countGoodStatusCodes(Path path) throws IOException {
        return (int) ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(new Hydrator<Integer, Integer>() {
                    @Override
                    public Integer start() { return 0; }
                    @Override
                    public Integer add(Integer target, String heading, Object value) {
                        if ("statusCode".equals(heading) && Integer.valueOf(200).equals(value)) {
                            return 1;
                        }
                        return 0;
                    }
                    @Override
                    public Integer finish(Integer target) { return target; }
                }),
                List.of("statusCode"))
                .count();
    }
}

