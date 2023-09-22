package nu.marginalia.loading.documents;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.io.processed.DocumentRecordParquetFileReader;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.linkdb.LinkdbWriter;
import nu.marginalia.linkdb.model.LdbUrlDetail;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.processed.DocumentRecordMetadataProjection;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class DocumentLoaderService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentLoaderService.class);

    private final LinkdbWriter linkdbWriter;

    @Inject
    public DocumentLoaderService(LinkdbWriter linkdbWriter) {
        this.linkdbWriter = linkdbWriter;
    }

    public boolean loadDocuments(
                             DomainIdRegistry domainIdRegistry,
                             ProcessHeartbeat processHeartbeat,
                             LoaderInputData inputData)
            throws IOException, SQLException
    {
        var documentFiles = inputData.listDocumentFiles();

        try (var taskHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("DOCUMENTS")) {

            int processed = 0;

            for (var file : documentFiles) {
                taskHeartbeat.progress("LOAD", processed++, documentFiles.size());

                loadDocumentsFromFile(domainIdRegistry, file);
            }
            taskHeartbeat.progress("LOAD", processed, documentFiles.size());
        }

        logger.info("Finished");

        return true;
    }

    private void loadDocumentsFromFile(DomainIdRegistry domainIdRegistry, Path file)
            throws SQLException, IOException
    {
        try (var stream = DocumentRecordParquetFileReader.streamMetadataProjection(file);
             LinkdbLoader loader = new LinkdbLoader(domainIdRegistry)
        )
        {
            logger.info("Loading document meta from {}", file);

            stream.forEach(loader::accept);
        }
    }

    class LinkdbLoader implements AutoCloseable {
        private final DomainIdRegistry domainIdRegistry;
        private final List<LdbUrlDetail> details = new ArrayList<>(1000);

        LinkdbLoader(DomainIdRegistry domainIdRegistry) {
            this.domainIdRegistry = domainIdRegistry;
        }

        @SneakyThrows
        public void accept(DocumentRecordMetadataProjection projection)
        {

            long urlId = UrlIdCodec.encodeId(
                    domainIdRegistry.getDomainId(projection.domain),
                    projection.ordinal
            );

            details.add(new LdbUrlDetail(
                    urlId,
                    new EdgeUrl(projection.url),
                    projection.title,
                    projection.description,
                    projection.quality,
                    projection.htmlStandard,
                    projection.htmlFeatures,
                    projection.pubYear,
                    projection.hash,
                    projection.getLength()
            ));

            if (details.size() > 100) {
                linkdbWriter.add(details);
                details.clear();
            }

        }

        @Override
        public void close() throws SQLException {
            if (!details.isEmpty()) {
                linkdbWriter.add(details);
            }
        }
    }

}
