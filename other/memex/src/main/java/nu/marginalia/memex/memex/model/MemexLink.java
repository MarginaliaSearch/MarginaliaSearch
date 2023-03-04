package nu.marginalia.memex.memex.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter @AllArgsConstructor
public class MemexLink {
    public final MemexNodeUrl dest;
    public final MemexNodeUrl src;
    public final String title;
    public final String section;
    public final MemexNodeHeadingId sectionId;

    public final MemexNodeUrl getUrl() {
        return src;
    }

    public String getDescription() {
        if (Objects.equals(title, section)) {
            return title;
        }
        return title + " - " + section;
    }
}
