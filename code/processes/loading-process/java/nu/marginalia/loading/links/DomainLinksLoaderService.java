package nu.marginalia.loading.links;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.linkgraph.io.DomainLinksWriter;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.processed.SlopDomainLinkRecord;
import nu.marginalia.model.processed.SlopPageRef;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

@Singleton
public class DomainLinksLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(DomainLinksLoaderService.class);

    private final DomainLinksWriter domainLinkDbWriter;

    @Inject
    public DomainLinksLoaderService(DomainLinksWriter domainLinkDbWriter) {
        this.domainLinkDbWriter = domainLinkDbWriter;
    }

    public boolean loadLinks(DomainIdRegistry domainIdRegistry,
                             ProcessHeartbeat heartbeat,
                             LoaderInputData inputData) throws IOException {

        try (var task = heartbeat.createAdHocTaskHeartbeat("LINKS")) {
            Collection<SlopPageRef<SlopDomainLinkRecord>> pageRefs = inputData.listDomainLinkPages();

            int processed = 0;

            for (var pageRef : pageRefs) {
                task.progress("LOAD", processed++, pageRefs.size());

                loadLinksFromFile(domainIdRegistry, pageRef);
            }

            task.progress("LOAD", processed, pageRefs.size());
        }
        catch (IOException e) {
            logger.error("Failed to load links", e);
            throw e;
        }

        logger.info("Finished");
        return true;
    }

    private void loadLinksFromFile(DomainIdRegistry domainIdRegistry, SlopPageRef pageRef) throws IOException {
        try (var domainLinkReader = new SlopDomainLinkRecord.Reader(pageRef);
             var linkLoader = new LinkLoader(domainIdRegistry))
        {
            logger.info("Loading links from {}:{}", pageRef.baseDir(), pageRef.page());

            domainLinkReader.forEach(linkLoader::accept);
        }
    }

    class LinkLoader implements AutoCloseable {
        private final DomainIdRegistry domainIdRegistry;

        public LinkLoader(DomainIdRegistry domainIdRegistry) {
            this.domainIdRegistry = domainIdRegistry;
        }

        @SneakyThrows
        void accept(SlopDomainLinkRecord record) {
            domainLinkDbWriter.write(
                    domainIdRegistry.getDomainId(record.source()),
                    domainIdRegistry.getDomainId(record.dest())
            );
        }

        @Override
        public void close() {}
    }
}
