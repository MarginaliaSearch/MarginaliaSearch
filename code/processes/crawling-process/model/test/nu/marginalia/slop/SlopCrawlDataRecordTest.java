package nu.marginalia.slop;

import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

class SlopCrawlDataRecordTest {

    @Test
    public void test() throws IOException {
//        Files.createDirectory(Path.of("/tmp/slackware.slop"));
//        SlopCrawlDataRecord.convertFromParquet(
//                Path.of("/home/vlofgren/Downloads/_storage_crawl-data__23-10-21T15_08_41.815_11_29_1129fa09-www.slackware.com.parquet"),
//                Path.of("/tmp/slackware.slop")
//        );

//        SlopTablePacker.packToSlopZip(Path.of("/tmp/steamstore.slop3"), Path.of("/tmp/steamstore3.slop.zip"));

        System.out.println("BEGIN Slop");
        for (int i = 0; i < 6; i++) {
            int sz = 0;
            Instant start = Instant.now();
            try (var reader = new SlopCrawlDataRecord.Reader(Path.of("/tmp/steamstore3.slop.zip")) {
                public boolean filter(String urlo, int status, String contentType) {
                    return contentType.startsWith("text/html");
                }
            }) {
                while (reader.hasRemaining()) {
                    sz += reader.get().httpStatus();
                }
            }
            Instant end = Instant.now();
            System.out.println("END Iter " + sz + " " + Duration.between(start, end));
        }
        System.out.println("END Slop");

        System.out.println("BEGIN Parquet");
        for (int i = 0; i < 6; i++) {
            Instant start = Instant.now();
            int sz = CrawledDocumentParquetRecordFileReader.stream(Path.of("/home/vlofgren/Downloads/_storage_crawl-data__23-10-21T15_08_43.750_3b_41_3b41e714-store.steampowered.com.parquet"))
                    .filter(record -> record.contentType.startsWith("text/html"))
                    .mapToInt(record -> record.httpStatus).sum();
            Instant end = Instant.now();
            System.out.println("END Iter " + sz + " " + Duration.between(start, end));
        }
        System.out.println("END Parquet");
    }
}