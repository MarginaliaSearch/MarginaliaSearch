package nu.marginalia.memex.memex.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Optional;

@AllArgsConstructor @Getter @EqualsAndHashCode
public class MemexExternalUrl implements MemexUrl {
    public final String url;

    public String toString() {
        return url;
    }
    @Override
    public Optional<MemexExternalUrl> getExternUrl() { return Optional.of(this); }
}
