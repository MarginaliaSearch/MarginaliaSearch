package nu.marginalia.wmsa.memex.change;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.memex.Memex;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;

import java.io.IOException;

@AllArgsConstructor @ToString
public class GemtextCreateOrMutate implements GemtextMutation {
    public final MemexNodeUrl doc;
    public final String text;
    public final GemtextMutation mutation;

    @Override
    public void visit(Memex memex) throws IOException {
        if (memex.getDocument(doc) == null) {
            memex.createNode(doc, text);
        }
        if (memex.getDocument(doc) == null)
            throw new IllegalStateException();

        mutation.visit(memex);
    }
}
