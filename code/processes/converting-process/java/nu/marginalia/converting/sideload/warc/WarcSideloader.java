package nu.marginalia.converting.sideload.warc;

import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.contenttype.ContentTypeParser;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class WarcSideloader implements SideloadSource, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WarcSideloader.class);

    private final SideloaderProcessing sideloaderProcessing;

    private final WarcReader reader;

    private final EdgeDomain domain;


    public WarcSideloader(Path warcFile,
                          SideloaderProcessing sideloaderProcessing)
    {
        try {
            this.sideloaderProcessing = sideloaderProcessing;
            this.reader = new WarcReader(warcFile);
            this.domain = sniffDomainFromWarc()
                    .orElseThrow(() -> new IOException("Could not identify domain from warc file"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = domain;
        ret.ip = "0.0.0.0";
        ret.state = DomainIndexingState.ACTIVE;
        ret.sizeloadSizeAdvice = 1000;

        return ret;
    }

    private Optional<EdgeDomain> sniffDomainFromWarc() throws IOException {
        try {
            for (var record : reader) {
                if (!(record instanceof WarcRequest request)) {
                    continue;
                }

                String target = request.target();
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    return Optional.of(new EdgeUrl(target).getDomain());
                }
            }
        } catch (URISyntaxException e) {
            return Optional.empty();
        } finally {
            reader.position(0);
        }
        return Optional.empty();
    }

    @Override
    public Iterator<ProcessedDocument> getDocumentsStream() {
        return reader.records()
                .filter(record -> record instanceof WarcResponse)
                .map(WarcResponse.class::cast)
                .filter(this::isRelevantResponse)
                .map(this::process)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .iterator();
    }

    private boolean isRelevantResponse(WarcResponse warcResponse) {
        try {
            HttpResponse httpResponse = warcResponse.http();
            if (httpResponse == null)
                return false;
            if (httpResponse.status() != 200)
                return false;
            if (!Objects.equals(httpResponse.contentType(), MediaType.HTML))
                return false;

            var url = new EdgeUrl(warcResponse.target());
            if (!Objects.equals(url.getDomain(), domain)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warn("Failed to process response", e);
        }

        return false;
    }

    private Optional<ProcessedDocument> process(WarcResponse response) {
        Optional<String> body = getBody(response);
        String url = response.target();

        // We trim "/index.html"-suffixes from the index if they are present,
        // since this is typically an artifact from document retrieval
        if (url.endsWith("/index.html")) {
            url = url.substring(0, url.length() - "index.html".length());
        }

        if (body.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(sideloaderProcessing
                    .processDocument(url,
                            body.get(),
                            List.of(),
                            new DomainLinks(),
                            GeneratorType.DOCS,
                            DocumentClass.SIDELOAD,
                            new LinkTexts(),
                            LocalDate.now().getYear(), // TODO: This should be the actual year of the document
                            10_000));
        }
        catch (Exception e) {
            logger.warn("Failed to process document", e);
            return Optional.empty();
        }
    }

    private Optional<String> getBody(WarcResponse response) {

        try {
            var http = response.http();


            // TODO: We should support additional encodings here
            try (var body = http.body()) {
                String contentType = http.headers().first("Content-Type").orElse(null);
                byte[] bytes = body.stream().readAllBytes();

                var ct = ContentTypeParser.parseContentType(contentType, bytes);
                return Optional.of(DocumentBodyToString.getStringData(ct, bytes));
            }
            catch (Exception ex) {
                logger.info("Failed to parse body", ex);
            }
        }
        catch (Exception e) {
            logger.warn("Failed to process response", e);
        }

        return Optional.empty();
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }

}
