package nu.marginalia.crawl.spec;

import lombok.SneakyThrows;
import nu.marginalia.io.crawlspec.CrawlSpecRecordParquetFileReader;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ParquetCrawlSpecProvider implements CrawlSpecProvider {
    private final List<Path> files;

    public ParquetCrawlSpecProvider(List<Path> files) {
        this.files = files;
    }

    @Override
    public int totalCount() throws IOException {
        int total = 0;
        for (var specs : files) {
            total += CrawlSpecRecordParquetFileReader.count(specs);
        }
        return total;
    }

    @Override
    public Stream<CrawlSpecRecord> stream() {
        return files.stream().flatMap(this::streamQuietly);
    }

    @SneakyThrows
    private Stream<CrawlSpecRecord> streamQuietly(Path file) {
        return CrawlSpecRecordParquetFileReader.stream(file);
    }
}
