package nu.marginalia.memex.memex.change;

import nu.marginalia.memex.memex.Memex;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

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
