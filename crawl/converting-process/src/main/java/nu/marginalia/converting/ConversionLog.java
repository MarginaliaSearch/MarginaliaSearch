package nu.marginalia.converting;

import com.github.luben.zstd.ZstdOutputStream;
import nu.marginalia.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.model.idx.EdgePageDocumentsMetadata;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.crawl.DocumentKeywords;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

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
