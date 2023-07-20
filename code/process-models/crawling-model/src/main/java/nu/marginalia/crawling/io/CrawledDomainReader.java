package nu.marginalia.crawling.io;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class CrawledDomainReader {
    private final Gson gson = GsonFactory.get();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ForkJoinPool pool = new ForkJoinPool(6);

    public CrawledDomainReader() {
    }

    public CrawledDomain read(Path path) throws IOException {
        DomainDataAssembler domainData = new DomainDataAssembler();

        try (var br = new BufferedReader(new InputStreamReader(new ZstdInputStream(new FileInputStream(path.toFile()))))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("//")) {
                    String identifier = line;
                    String data = br.readLine();

                    pool.execute(() -> deserializeLine(identifier, data, domainData));
                }
            }
        }

        while (!pool.awaitQuiescence(1, TimeUnit.SECONDS));

        return domainData.assemble();
    }


    private void deserializeLine(String identifier, String data, DomainDataAssembler assembler) {
        if (null == data) {
            return;
        }
        if (identifier.equals(CrawledDomain.SERIAL_IDENTIFIER)) {
            assembler.acceptDomain(gson.fromJson(data, CrawledDomain.class));
        } else if (identifier.equals(CrawledDocument.SERIAL_IDENTIFIER)) {
            assembler.acceptDoc(gson.fromJson(data, CrawledDocument.class));
        }
    }

    public Optional<CrawledDomain> readOptionally(Path path) {
        try {
            return Optional.of(read(path));
        }
        catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static class DomainDataAssembler {
        private CrawledDomain domainPrototype;
        private final List<CrawledDocument> docs = new ArrayList<>();

        public synchronized void acceptDomain(CrawledDomain domain) {
            this.domainPrototype = domain;
        }

        public synchronized void acceptDoc(CrawledDocument doc) {
            docs.add(doc);
        }

        public synchronized CrawledDomain assemble() {
            if (!docs.isEmpty()) {
                if (domainPrototype.doc == null)
                    domainPrototype.doc = new ArrayList<>();

                domainPrototype.doc.addAll(docs);
            }
            return domainPrototype;
        }
    }
}
