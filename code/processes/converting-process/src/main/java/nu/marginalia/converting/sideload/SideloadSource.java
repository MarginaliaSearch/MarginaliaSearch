package nu.marginalia.converting.sideload;

import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;

import java.util.Iterator;

public interface SideloadSource {
    ProcessedDomain getDomain();
    Iterator<ProcessedDocument> getDocumentsStream();

    default String domainName() {
        return getDomain().domain.toString();
    }
}
