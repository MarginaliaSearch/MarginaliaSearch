package nu.marginalia.io.processed;

import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import nu.marginalia.model.processed.DomainLinkRecord;
import nu.marginalia.model.processed.DomainRecord;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DomainLinkRecordParquetFileReader {
    @NotNull
    public static Stream<DomainLinkRecord> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(DomainLinkRecord.newHydrator()));
    }

    @NotNull
    public static Set<String> getDestDomainNames(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                        HydratorSupplier.constantly(DomainLinkRecord.newDestDomainHydrator()),
                        List.of("dest"))
                .collect(Collectors.toSet());
    }

}
