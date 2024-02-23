package nu.marginalia.io.processed;

import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import nu.marginalia.model.processed.DomainRecord;
import nu.marginalia.model.processed.DomainWithIp;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class DomainRecordParquetFileReader {

    @NotNull
    public static Stream<DomainRecord> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(DomainRecord.newHydrator()));
    }

    @NotNull
    public static List<DomainWithIp> getBasicDomainInformation(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(DomainRecord.newDomainNameHydrator()),
                List.of("domain", "ip"))
                .toList();
    }


}
