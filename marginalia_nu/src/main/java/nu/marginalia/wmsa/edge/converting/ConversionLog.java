package nu.marginalia.wmsa.edge.converting;

import com.github.luben.zstd.ZstdOutputStream;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocumentWithError;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class ConversionLog implements AutoCloseable, Interpreter {



    private final PrintWriter writer;

    public ConversionLog(Path rootDir) throws IOException {
        String fileName = String.format("conversion-log-%s.zstd", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        Path logFile = rootDir.resolve(fileName);

        writer = new PrintWriter(new ZstdOutputStream(
                new BufferedOutputStream(Files.newOutputStream(logFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE))));
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public void loadUrl(EdgeUrl[] url) {}

    @Override
    public void loadDomain(EdgeDomain[] domain) {}

    @Override
    public void loadRssFeed(EdgeUrl[] rssFeed) {}

    @Override
    public void loadDomainLink(DomainLink[] links) {}

    @Override
    public void loadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, String ip) {}

    @Override
    public void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument) {}

    @Override
    public synchronized void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError) {
        writer.printf("%s\t%s\n", loadProcessedDocumentWithError.url(), loadProcessedDocumentWithError.reason());
    }

    @Override
    public void loadKeywords(EdgeUrl url, EdgePageDocumentsMetadata metadata, DocumentKeywords words) {}

    @Override
    public void loadDomainRedirect(DomainLink link) {}
}
