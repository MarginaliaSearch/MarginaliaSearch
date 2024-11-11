package nu.marginalia.loading.links;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.linkgraph.io.DomainLinksWriter;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.processed.SlopDomainLinkRecord;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.slop.SlopTable;
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

        try (var task = heartbeat.createAdHocTaskHeartbeat("LINKS");
             var linkLoader = new LinkLoader(domainIdRegistry))
        {
            Collection<SlopTable.Ref<SlopDomainLinkRecord>> pageRefs = inputData.listDomainLinkPages();

            int processed = 0;

            for (var pageRef : pageRefs) {
                task.progress("LOAD", processed++, pageRefs.size());

                try (var domainLinkReader = new SlopDomainLinkRecord.Reader(pageRef))
                {
                    while (domainLinkReader.hasMore()) {
                        var link = domainLinkReader.next();
                        linkLoader.accept(link.source(), link.dest());
                    }
                }
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


    private class LinkLoader implements AutoCloseable {
        private final DomainIdRegistry domainIdRegistry;

        public LinkLoader(DomainIdRegistry domainIdRegistry) {
            this.domainIdRegistry = domainIdRegistry;
        }

        void accept(String source, String dest) throws IOException {
            domainLinkDbWriter.write(
                    domainIdRegistry.getDomainId(source),
                    domainIdRegistry.getDomainId(dest)
            );
        }

        @Override
        public void close() {}
    }
}
