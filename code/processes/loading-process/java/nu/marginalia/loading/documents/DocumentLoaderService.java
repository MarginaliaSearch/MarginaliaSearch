package nu.marginalia.loading.documents;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.slop.SlopTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class DocumentLoaderService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentLoaderService.class);

    private final DocumentDbWriter documentDbWriter;

    @Inject
    public DocumentLoaderService(DocumentDbWriter documentDbWriter) {
        this.documentDbWriter = documentDbWriter;
    }

    public boolean loadDocuments(
                             DomainIdRegistry domainIdRegistry,
                             ProcessHeartbeat processHeartbeat,
                             LoaderInputData inputData)
            throws IOException, SQLException
    {
        Collection<SlopTable.Ref<SlopDocumentRecord>> pageRefs = inputData.listDocumentFiles();

        try (var taskHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("DOCUMENTS")) {

            int processed = 0;

            for (var pageRef : pageRefs) {
                taskHeartbeat.progress("LOAD", processed++, pageRefs.size());

                try (var reader = new SlopDocumentRecord.MetadataReader(pageRef);
                     LinkdbLoader loader = new LinkdbLoader(domainIdRegistry))
                {
                    while (reader.hasMore()) {
                        loader.accept(reader.next());
                    }
                }
            }
            taskHeartbeat.progress("LOAD", processed, pageRefs.size());
        } catch (IOException e) {
            logger.error("Failed to load documents", e);
            throw e;
        }

        logger.info("Finished");

        return true;
    }

    class LinkdbLoader implements AutoCloseable {
        private final DomainIdRegistry domainIdRegistry;
        private final List<DocdbUrlDetail> details = new ArrayList<>(1000);

        LinkdbLoader(DomainIdRegistry domainIdRegistry) {
            this.domainIdRegistry = domainIdRegistry;
        }

        public void accept(SlopDocumentRecord.MetadataProjection projection)
        {

            long urlId = UrlIdCodec.encodeId(
                    domainIdRegistry.getDomainId(projection.domain()),
                    projection.ordinal()
            );

            var parsedUrl = EdgeUrl.parse(projection.url());
            if (parsedUrl.isEmpty()) {
                logger.error("Failed to parse URL: {}", projection.url());
                return;
            }

            try {
                documentDbWriter.add(new DocdbUrlDetail(
                        urlId,
                        parsedUrl.get(),
                        projection.title(),
                        projection.description(),
                        projection.quality(),
                        projection.htmlStandard(),
                        projection.htmlFeatures(),
                        projection.pubYear(),
                        projection.hash(),
                        projection.length()
                ));

                if (details.size() > 100) {
                    documentDbWriter.add(details);
                    details.clear();
                }
            }
            catch (Exception e) {
                logger.error("Failed to add document", e);
            }

        }

        @Override
        public void close() throws SQLException {
            if (!details.isEmpty()) {
                documentDbWriter.add(details);
            }
        }
    }

}
