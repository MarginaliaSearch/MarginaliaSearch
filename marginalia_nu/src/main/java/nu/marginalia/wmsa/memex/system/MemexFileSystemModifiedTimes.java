package nu.marginalia.wmsa.memex.system;

import com.google.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MemexFileSystemModifiedTimes {

    private final Map<Path, Long> modifiedTimes = new ConcurrentHashMap<>();

    public boolean isFreshUpdate(Path node) throws IOException {
        long mtime = Files.getLastModifiedTime(node).toMillis();

        return !Objects.equals(modifiedTimes.put(node, mtime), mtime);
    }

}
