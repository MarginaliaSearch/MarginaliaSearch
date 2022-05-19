package nu.marginalia.gemini.gmi.line;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;

import java.util.Optional;
import java.util.function.Function;

@AllArgsConstructor
@Getter
@ToString
public class GemtextHeading extends AbstractGemtextLine {
    private final MemexNodeHeadingId level;
    private final String name;
    private final MemexNodeHeadingId heading;

    public <T> Optional<T> mapHeading(Function<GemtextHeading, T> mapper) {
        return Optional.of(mapper.apply(this));
    }

    @Override
    public <T> T visit(GemtextLineVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public boolean breaksTask() {
        return true;
    }
}
