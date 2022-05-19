package nu.marginalia.wmsa.memex.change;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.memex.Memex;
import nu.marginalia.gemini.gmi.GemtextDocument;
import nu.marginalia.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@AllArgsConstructor @ToString
public class GemtextReplace implements GemtextMutation {
    public final MemexNodeUrl doc;
    public final MemexNodeHeadingId id;
    public final String[] lines;

    private static final Logger logger = LoggerFactory.getLogger(GemtextPrepend.class);

    @Override
    public void visit(Memex memex) throws IOException {
        memex.updateNode(doc, calculateReplace(memex.getDocument(doc)));
    }

    public String calculateReplace(GemtextDocument document) {
        StringBuilder result = new StringBuilder();
        var renderer = new GemtextRendererFactory().gemtextRendererAsIs();

        var lines = document.getLines();
        int i = 0;
        for (; i < lines.length; i++) {
            var item = lines[i];

            if (item.getHeading().isChildOf(id)) {
                break;
            }
            else {
                result.append(item.visit(renderer)).append('\n');
            }
        }

        if (i == lines.length) {
            logger.error("Heading not found in replacing heading {} of {}, writing change-data to file",
                    id, document.getUrl());
            result.append("# Error! Replace failed!\n");
        }

        for (;i < lines.length && lines[i].getHeading().isChildOf(id); i++) {
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
