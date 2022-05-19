package nu.marginalia.gemini.gmi.line;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexUrl;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

@AllArgsConstructor @Getter @ToString
public class GemtextLink extends AbstractGemtextLine {
    private final MemexUrl url;

    @Nullable
    private final String title;
    private final MemexNodeHeadingId heading;

    public <T> Optional<T> mapLink(Function<GemtextLink, T> mapper) {
        return Optional.ofNullable(mapper.apply(this));
    }

    @Override
    public <T> T visit(GemtextLineVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public boolean breaksTask() {
        return false;
    }
}
