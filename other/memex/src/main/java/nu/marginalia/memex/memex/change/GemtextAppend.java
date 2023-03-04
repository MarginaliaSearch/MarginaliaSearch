package nu.marginalia.memex.memex.change;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.memex.memex.Memex;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

import java.io.IOException;

@AllArgsConstructor @ToString
public class GemtextAppend implements GemtextMutation {
    public final MemexNodeUrl doc;
    public final MemexNodeHeadingId id;
    public final String[] lines;

    @Override
    public void visit(Memex memex) throws IOException {
        memex.updateNode(doc, calculateAppend(memex.getDocument(doc)));
    }

    public String calculateAppend(GemtextDocument document) {

        StringBuilder result = new StringBuilder();
        var renderer = new GemtextRendererFactory().gemtextRendererAsIs();

        var lines = document.getLines();

        int i = 0;
        // Copy from before heading
        for (; i < lines.length; i++) {
            var item = lines[i];

            if (item.getHeading().isChildOf(id)) {
                break;
            }
            else {
                result.append(item.visit(renderer)).append('\n');
            }
        }

        // Copy contents of heading
        for (; i < lines.length; i++) {
            var item = lines[i];

            if (!item.getHeading().isChildOf(id)) {
                break;
            }
            else {
                result.append(item.visit(renderer)).append('\n');
            }
        }

        // Insert new lines
        for (String newLine : this.lines) {
            result.append(newLine).append('\n');
        }

        // Copy contents from after heading
        for (;i < lines.length; i++) {
            var item = lines[i];
            result.append(item.visit(renderer)).append('\n');
        }

        return result.toString();
    }

}
