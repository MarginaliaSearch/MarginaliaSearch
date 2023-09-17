package nu.marginalia.converting.sideload.dirtree;

import lombok.SneakyThrows;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class DirtreeSideloader implements SideloadSource, AutoCloseable {
    private final Path dirBase;
    private final String domainName;
    private final String urlBase;

    private final List<String> extraKeywords;
    private final SideloaderProcessing sideloaderProcessing;

    private final Stream<Path> filesStream;

    public DirtreeSideloader(Path dirBase,
                             String domainName,
                             String urlBase,
                             List<String> extraKeywords,
                             SideloaderProcessing sideloaderProcessing)
    throws IOException
    {
        this.dirBase = dirBase;
        this.domainName = domainName;
        this.urlBase = urlBase + (urlBase.endsWith("/") ? "" : "/");
        this.filesStream = Files.walk(dirBase);
        this.extraKeywords = extraKeywords;
        this.sideloaderProcessing = sideloaderProcessing;
    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = new EdgeDomain(domainName);
        ret.ip = "0.0.0.0";
        ret.state = DomainIndexingState.ACTIVE;

        return ret;
    }

    @Override
    public Iterator<ProcessedDocument> getDocumentsStream() {
        return filesStream
                .filter(Files::isRegularFile)
                .filter(this::isHtmlFile)
                .map(this::process)
                .iterator();
    }

    private boolean isHtmlFile(Path path) {
        final String name = path.toFile().getName().toLowerCase();

        return name.endsWith(".html") || name.endsWith(".htm");
    }

    @SneakyThrows
    private ProcessedDocument process(Path path) {
        String body = Files.readString(path);
        String url = urlBase + dirBase.relativize(path);

        return sideloaderProcessing
                .processDocument(url, body, extraKeywords, 10_000);
    }

    @Override
    public void close() throws Exception {
        filesStream.close();
    }

}
