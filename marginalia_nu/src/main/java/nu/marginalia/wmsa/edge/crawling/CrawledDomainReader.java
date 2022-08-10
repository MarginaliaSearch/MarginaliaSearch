package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CrawledDomainReader {
    private final Gson gson = new GsonBuilder().create();
    private static final Logger logger = LoggerFactory.getLogger(CrawledDomainReader.class);

    public CrawledDomainReader() {
    }

    public CrawledDomain read(Path path) throws IOException {
        List<CrawledDocument> docs = new ArrayList<>();
        CrawledDomain domain = null;
        try (var br = new BufferedReader(new InputStreamReader(new ZstdInputStream(new BufferedInputStream(new FileInputStream(path.toFile())))))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("//")) {
                    String nextLine = br.readLine();
                    if (nextLine == null) break;

                    if (line.equals(CrawledDomain.SERIAL_IDENTIFIER)) {
                        domain = gson.fromJson(nextLine, CrawledDomain.class);
                    } else if (line.equals(CrawledDocument.SERIAL_IDENTIFIER)) {
                        docs.add(gson.fromJson(nextLine, CrawledDocument.class));
                    }
                }
                else if (line.charAt(0) == '{') {
                    domain = gson.fromJson(line, CrawledDomain.class);
                }
            }
        }

        if (domain == null) {
            return null;
        }
        domain.doc.addAll(docs);
        return domain;
    }

    public CrawledDomain readRuntimeExcept(Path path) {
        try {
            return read(path);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
