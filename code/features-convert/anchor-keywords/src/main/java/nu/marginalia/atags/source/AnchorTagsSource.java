package nu.marginalia.atags.source;

import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.model.EdgeDomain;

public interface AnchorTagsSource extends AutoCloseable {
    DomainLinks getAnchorTags(EdgeDomain domain);

    default void close() throws Exception {}
}
