package nu.marginalia.wmsa.memex.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.file.Path;

@AllArgsConstructor @Getter
public class MemexImage {
    public final MemexNodeUrl path;
    public final Path realPath;

}
