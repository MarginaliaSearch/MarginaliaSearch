package nu.marginalia.io.processed;

import blue.strategic.parquet.HydratorSupplier;
import blue.strategic.parquet.ParquetReader;
import nu.marginalia.codec.processed.ProcessedDomainDataDomainNameHydrator;
import nu.marginalia.codec.processed.ProcessedDomainDataHydrator;
import nu.marginalia.model.processed.ProcessedDomainData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ProcessedDomainParquetFileReader {

    @NotNull
    public static Stream<ProcessedDomainData> stream(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(new ProcessedDomainDataHydrator()));
    }

    @NotNull
    public static List<String> getDomainNames(Path path) throws IOException {
        return ParquetReader.streamContent(path.toFile(),
                HydratorSupplier.constantly(new ProcessedDomainDataDomainNameHydrator()),
                List.of("domain"))
                .toList();
    }


}
