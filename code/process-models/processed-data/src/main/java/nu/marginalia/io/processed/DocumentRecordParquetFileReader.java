package nu.marginalia.io.processed;

import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import nu.marginalia.model.processed.DocumentRecord;
import nu.marginalia.model.processed.DocumentRecordKeywordsProjection;
import nu.marginalia.model.processed.DocumentRecordMetadataProjection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DocumentRecordParquetFileReader {

    @NotNull
    public static Stream<DocumentRecord> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(DocumentRecord.newHydrator()));
    }

    @NotNull
    public static Stream<DocumentRecordKeywordsProjection> streamKeywordsProjection(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(DocumentRecordKeywordsProjection.newHydrator()),
                DocumentRecordKeywordsProjection.requiredColumns()
                );
    }

    @NotNull
    public static Stream<DocumentRecordMetadataProjection> streamMetadataProjection(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(DocumentRecordMetadataProjection.newHydrator()),
                DocumentRecordMetadataProjection.requiredColumns()
        );
    }
}
