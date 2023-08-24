package nu.marginalia.converting.sideload;

import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeUrl;

import java.util.Iterator;

public interface SideloadSource {
    ProcessedDomain getDomain();
    Iterator<ProcessedDocument> getDocumentsStream();

    String getId();
}
