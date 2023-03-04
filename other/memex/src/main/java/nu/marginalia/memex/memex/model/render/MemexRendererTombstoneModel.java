package nu.marginalia.memex.memex.model.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.memex.memex.model.MemexLink;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

import java.util.List;

@AllArgsConstructor @Getter
public class MemexRendererTombstoneModel {
    private final MemexNodeUrl url;
    private final String message;
    private final String redirect;
    public final List<MemexLink> backlinks;
}
