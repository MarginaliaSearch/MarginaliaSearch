package nu.marginalia.wmsa.edge.index.client;

import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

public interface EdgeIndexWriterClient extends AutoCloseable {

    void putWords(Context ctx, EdgeId<EdgeDomain> domain, EdgeId<EdgeUrl> url,
                  DocumentKeywords wordSets, int writer);
}
