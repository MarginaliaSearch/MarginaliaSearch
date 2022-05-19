package nu.marginalia.wmsa.memex.model;

import java.util.Optional;
import java.util.function.Consumer;

public interface MemexUrl {
    String getUrl();

    default void visitNodeUrl(Consumer<MemexNodeUrl> fn) {}
    default void visitExternalUrl(Consumer<MemexExternalUrl> fn) {}
    default Optional<MemexNodeUrl> getNodeUrl() { return Optional.empty(); }
    default Optional<MemexExternalUrl> getExternUrl() { return Optional.empty(); }
}
