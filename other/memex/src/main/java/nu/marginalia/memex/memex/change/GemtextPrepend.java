package nu.marginalia.memex.memex.change;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.memex.memex.Memex;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@AllArgsConstructor @ToString
public class GemtextPrepend implements GemtextMutation {
    public final MemexNodeUrl doc;
    public final MemexNodeHeadingId id;
    public final String[] lines;

    private static final Logger logger = LoggerFactory.getLogger(GemtextPrepend.class);

    @Override
    public void visit(Memex memex) throws IOException {
        memex.updateNode(doc, calculatePrepend(memex.getDocument(doc)));
    }

    public String calculatePrepend(GemtextDocument document) {
        StringBuilder result = new StringBuilder();
        var renderer = new GemtextRendererFactory().gemtextRendererAsIs();
        var lines = document.getLines();
        int i = 0;
        for (; i < lines.length; i++) {
            var item = lines[i];

            if (item.getHeading().isChildOf(id)) {
                if (!id.equals(MemexNodeHeadingId.ROOT)) {
                    result.append(item.visit(renderer)).append('\n');
                    i++;
                }
                break;
            }
            else {
                result.append(item.visit(renderer)).append('\n');
            }
        }

        if (i == lines.length) {
            logger.warn("Heading not found in prepending heading {} of {}, falling back to append-like behavior",
                    id, document.getUrl());
        }
        for (String newLine : this.lines) {
            result.append(newLine).append('\n');
        }

        for (;i < lines.length; i++) {
            var item = lines[i];
            result.append(item.visit(renderer)).append('\n');
        }

        return result.toString();
    }
}
