package nu.marginalia.memex.gemini.gmi.line;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;

import java.util.List;

@AllArgsConstructor @Getter @ToString
public class GemtextList extends AbstractGemtextLine {
    private final List<String> items;
    private final MemexNodeHeadingId heading;

    @Override
    public <T> T visit(GemtextLineVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public boolean breaksTask() {
        return true;
    }
}
