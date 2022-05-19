package nu.marginalia.gemini.gmi.line;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeTaskId;
import nu.marginalia.wmsa.memex.model.MemexTaskState;
import nu.marginalia.wmsa.memex.model.MemexTaskTags;

import java.util.Optional;
import java.util.function.Function;

@AllArgsConstructor @Getter @ToString
public class GemtextTask extends AbstractGemtextLine {
    private final MemexNodeTaskId id;
    private final String task;
    private final MemexNodeHeadingId heading;
    private final MemexTaskTags tags;

    public MemexTaskState getState() {
        return MemexTaskState.of(tags);
    }

    public int getLevel() {
        return id.level();
    }
    @Override
    public <T> T visit(GemtextLineVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean breaksTask() {
        return true;
    }

    @Override
    public <T> Optional<T> mapTask(Function<GemtextTask, T> mapper) {
        return Optional.of(mapper.apply(this));
    }
}
