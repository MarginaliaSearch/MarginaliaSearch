package nu.marginalia.memex.gemini.gmi.line;

import nu.marginalia.memex.memex.model.MemexNodeHeadingId;

import java.util.Optional;
import java.util.function.Function;

public abstract class AbstractGemtextLine {
    public <T> Optional<T> mapLink(Function<GemtextLink, T> mapper) {
         return Optional.empty();
     }
    public <T> Optional<T> mapHeading(Function<GemtextHeading, T> mapper) { return Optional.empty(); }
    public <T> Optional<T> mapTask(Function<GemtextTask, T> mapper) { return Optional.empty(); }
    public abstract <T> T visit(GemtextLineVisitor<T> visitor);

    public abstract boolean breaksTask();
    public abstract MemexNodeHeadingId getHeading();
}
