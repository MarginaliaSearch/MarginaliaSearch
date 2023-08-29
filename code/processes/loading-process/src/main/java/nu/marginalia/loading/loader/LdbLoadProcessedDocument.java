package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
import nu.marginalia.linkdb.LinkdbStatusWriter;
import nu.marginalia.linkdb.LinkdbWriter;
import nu.marginalia.linkdb.model.LdbUrlDetail;
import nu.marginalia.linkdb.model.UrlStatus;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LdbLoadProcessedDocument {
    private static final Logger logger = LoggerFactory.getLogger(LdbLoadProcessedDocument.class);
    private final LinkdbWriter linkdbWriter;
    private final LinkdbStatusWriter linkdbStatusWriter;

    @Inject
    public LdbLoadProcessedDocument(LinkdbWriter linkdbWriter,
                                    LinkdbStatusWriter linkdbStatusWriter
                                    ) {
        this.linkdbWriter = linkdbWriter;
        this.linkdbStatusWriter = linkdbStatusWriter;
    }

    public void load(LoaderData data, List<LoadProcessedDocument> documents) {
        var details = new ArrayList<LdbUrlDetail>();

        int domainId = data.getTargetDomainId();
        var statusList = new ArrayList<UrlStatus>();

        for (var document : documents) {
            long id = UrlIdCodec.encodeId(domainId, document.ordinal());
            details.add(new LdbUrlDetail(
                    id,
                    document.url(),
                    document.title(),
                    document.description(),
                    document.quality(),
                    document.standard(),
                    document.htmlFeatures(),
                    document.pubYear(),
                    document.hash(),
                    document.length()
            ));
            statusList.add(new UrlStatus(id, document.url(), document.state().toString(), null));
        }

        try {
            linkdbWriter.add(details);
        }
        catch (SQLException ex) {
            logger.warn("Failed to add processed documents to linkdb", ex);
        }
    }

    public void loadWithError(LoaderData data, List<LoadProcessedDocumentWithError> documents) {
        var statusList = new ArrayList<UrlStatus>();
        int domainId = data.getTargetDomainId();

        for (var document : documents) {
            statusList.add(new UrlStatus(
                    UrlIdCodec.encodeId(domainId, document.ordinal()),
                    document.url(),
                    document.state().toString(),
                    document.reason()
            ));
        }

        try {
            linkdbStatusWriter.add(statusList);
        }
        catch (SQLException ex) {
            logger.warn("Failed to add processed documents to linkdb", ex);
        }
    }

}
