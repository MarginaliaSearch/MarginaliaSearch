package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CrawledDomainReader {
    private final Gson gson = new GsonBuilder().create();

    public CrawledDomainReader() {
    }

    public CrawledDomain read(Path path) throws IOException {
        List<CrawledDocument> docs = new ArrayList<>();
        CrawledDomain domain = null;


        try (var br = new BufferedReader(new InputStreamReader(new ZstdInputStream(new FileInputStream(path.toFile()))))) {
            br.mark(2);
            boolean legacy = '{' == br.read();
            br.reset();

            if (legacy) {
                domain = gson.fromJson(br, CrawledDomain.class);
            }
            else {
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
                    } else if (line.charAt(0) == '{') {
                        domain = gson.fromJson(line, CrawledDomain.class);
                    }
                }
            }
        }

        if (domain == null) {
            return null;
        }

        if (!docs.isEmpty()) {
            if (domain.doc == null)
                domain.doc = new ArrayList<>();

            domain.doc.addAll(docs);
        }
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
