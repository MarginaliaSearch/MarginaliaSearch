package nu.marginalia.slop;

import nu.marginalia.contenttype.ContentType;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

class SlopCrawlDataRecordTest {

    @Test
    public void test() throws IOException {

//        Files.deleteIfExists(Path.of("/tmp/steam.slop.zip"));
//        SlopCrawlDataRecord.convertFromParquet(
//                Path.of("/home/vlofgren/Downloads/_storage_crawl-data__23-10-21T15_08_43.750_3b_41_3b41e714-store.steampowered.com.parquet"),
//                Path.of("/tmp/steam.slop.zip")
//        );
//        long st = 0;
//        try (var reader = new SlopCrawlDataRecord.FilteringReader(Path.of("/tmp/steam.slop.zip")) {
//            public boolean filter(String url, int status, String contentType) {
//                return contentType.startsWith("text/html");
//            }
//        }) {
//            Instant start = Instant.now();
//            while (reader.hasRemaining()) {
//                var next = reader.get();
//                byte[] body = next.body();
//
//                st += Jsoup.parse(new String(body)).title().length();
//            }
//            System.out.println(st + " " + Duration.between(start, Instant.now()));
//        }

        long st = 0;
        try (var reader = new SlopCrawlDataRecord.FilteringReader(Path.of("/tmp/steam.slop.zip")) {
            public boolean filter(String url, int status, String contentType) {
                return contentType.startsWith("text/html");
            }
        }) {
            Instant start = Instant.now();
            while (reader.hasRemaining()) {
                var next = reader.get();
                byte[] body = next.body();

                st += Jsoup.parse(new ByteArrayInputStream(body), ContentType.parse(next.contentType()).asCharset().name(), next.url()).title().length();

            }
            System.out.println(Duration.between(start, Instant.now()));
        }

//        System.out.println("BEGIN Slop");
//        for (int i = 0; i < 6; i++) {
//            int sz = 0;
//            Instant start = Instant.now();
//            try (var reader = new SlopCrawlDataRecord.Reader(Path.of("/tmp/steam.slop.zip")) {
//                public boolean filter(String url, int status, String contentType) {
//                    return contentType.startsWith("text/html");
//                }
//            }) {
//                while (reader.hasRemaining()) {
//                    sz += reader.get().httpStatus();
//                }
//            }
//            Instant end = Instant.now();
//            System.out.println("END Iter " + sz + " " + Duration.between(start, end));
//        }
//        System.out.println("END Slop");
//
//        System.out.println("BEGIN Parquet");
//        for (int i = 0; i < 6; i++) {
//            Instant start = Instant.now();
//            int sz = CrawledDocumentParquetRecordFileReader.stream(Path.of("/home/vlofgren/Downloads/_storage_crawl-data__23-10-21T15_08_43.750_3b_41_3b41e714-store.steampowered.com.parquet"))
//                    .filter(record -> record.contentType.startsWith("text/html"))
//                    .mapToInt(record -> record.httpStatus).sum();
//            Instant end = Instant.now();
//            System.out.println("END Iter " + sz + " " + Duration.between(start, end));
//        }
//        System.out.println("END Parquet");
    }
}