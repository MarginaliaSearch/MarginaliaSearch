package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;

public class CrawledDomainReader {
    private final Gson gson = new GsonBuilder().create();
    private static final Logger logger = LoggerFactory.getLogger(CrawledDomainReader.class);

    public CrawledDomainReader() {
    }

    public CrawledDomain read(Path path) throws IOException {
        try (var br = new BufferedReader(new InputStreamReader(new ZstdInputStream(new BufferedInputStream(new FileInputStream(path.toFile())))))) {
            return gson.fromJson(br, CrawledDomain.class);
        }
    }

}
