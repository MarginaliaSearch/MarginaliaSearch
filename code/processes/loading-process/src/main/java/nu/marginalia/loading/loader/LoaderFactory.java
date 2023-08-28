package nu.marginalia.loading.loader;

import com.google.inject.Inject;

public class LoaderFactory {
    private final OldDomains oldDomains;
    private final SqlLoadDomains sqlLoadDomains;
    private final SqlLoadDomainLinks sqlLoadDomainLinks;
    private final SqlLoadProcessedDomain sqlLoadProcessedDomain;
    private final LdbLoadProcessedDocument sqlLoadProcessedDocument;
    private final SqlLoadDomainMetadata sqlLoadDomainMetadata;
    private final IndexLoadKeywords indexLoadKeywords;

    @Inject
    public LoaderFactory(OldDomains oldDomains,
                         SqlLoadDomains sqlLoadDomains,
                         SqlLoadDomainLinks sqlLoadDomainLinks,
                         SqlLoadProcessedDomain sqlLoadProcessedDomain,
                         LdbLoadProcessedDocument sqlLoadProcessedDocument,
                         SqlLoadDomainMetadata sqlLoadDomainMetadata,
                         IndexLoadKeywords indexLoadKeywords) {
        this.oldDomains = oldDomains;

        this.sqlLoadDomains = sqlLoadDomains;
        this.sqlLoadDomainLinks = sqlLoadDomainLinks;
        this.sqlLoadProcessedDomain = sqlLoadProcessedDomain;
        this.sqlLoadProcessedDocument = sqlLoadProcessedDocument;
        this.sqlLoadDomainMetadata = sqlLoadDomainMetadata;
        this.indexLoadKeywords = indexLoadKeywords;
    }

    public Loader create(int sizeHint) {
        return new Loader(sizeHint, sqlLoadDomains, sqlLoadDomainLinks, sqlLoadProcessedDomain, sqlLoadProcessedDocument, sqlLoadDomainMetadata, indexLoadKeywords);
    }
}
