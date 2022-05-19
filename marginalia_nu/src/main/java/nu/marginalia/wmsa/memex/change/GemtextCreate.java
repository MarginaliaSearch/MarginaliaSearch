package nu.marginalia.wmsa.memex.change;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.memex.Memex;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;

import java.io.IOException;

@AllArgsConstructor @ToString
public class GemtextCreate implements GemtextMutation {
    public final MemexNodeUrl doc;
    public final String text;

    @Override
    public void visit(Memex memex) throws IOException {
        memex.createNode(doc, text);
    }
}
