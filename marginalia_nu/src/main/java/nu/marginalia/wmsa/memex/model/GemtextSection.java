package nu.marginalia.wmsa.memex.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public class GemtextSection {
    public final MemexNodeHeadingId id;
    public final GemtextSectionAction action;
    public final String[] lines;
}
