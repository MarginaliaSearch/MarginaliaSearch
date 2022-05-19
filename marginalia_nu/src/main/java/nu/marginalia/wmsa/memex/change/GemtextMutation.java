package nu.marginalia.wmsa.memex.change;

import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.memex.Memex;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.model.MemexUrl;

import java.io.IOException;

public interface GemtextMutation {
    void visit(Memex memex) throws IOException;

    static GemtextMutation createOrAppend(MemexNodeUrl url, String template, MemexNodeHeadingId heading, String... lines) {
        return new GemtextCreateOrMutate(url, template, new GemtextAppend(url, heading, lines));
    }
    static GemtextMutation createOrPrepend(MemexNodeUrl url, String template, MemexNodeHeadingId heading, String... lines) {
        return new GemtextCreateOrMutate(url, template, new GemtextPrepend(url, heading, lines));
    }
}
