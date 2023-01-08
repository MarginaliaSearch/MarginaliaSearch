package nu.marginalia.util.array.functional;

import java.io.IOException;

public interface IntIOTransformer {
    int transform(long pos, int old) throws IOException;
}
