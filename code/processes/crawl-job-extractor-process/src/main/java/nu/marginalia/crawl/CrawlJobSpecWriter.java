package nu.marginalia.crawl;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.model.gson.GsonFactory;

import java.io.*;
import java.nio.file.Path;

public class CrawlJobSpecWriter implements AutoCloseable {

    private final PrintWriter writer;
    private final Gson gson = GsonFactory.get();

    public CrawlJobSpecWriter(Path fileName) throws IOException {
        writer = new PrintWriter(new ZstdOutputStream(new BufferedOutputStream(new FileOutputStream(fileName.toFile()))));
    }

    public void accept(CrawlingSpecification crawlingSpecification) {
        gson.toJson(crawlingSpecification, writer);
    }

    public void close() {
        writer.close();
    }
}
