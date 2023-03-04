package nu.marginalia.memex.memex.system;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.*;

public class MemexFileSystemMonitor {
    private final WatchService watchService;
    private final Set<MemexNodeUrl> updatedUrls = new HashSet<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<WatchKey, Path> roots = new ConcurrentHashMap<>();
    private final Path memexRoot;

    @Inject
    public MemexFileSystemMonitor(@Named("memex-root") Path monitorPath) throws IOException {
        this.memexRoot = monitorPath;
        this.watchService = FileSystems.getDefault().newWatchService();

        registerWatcher(monitorPath);

        try (var files = Files.walk(monitorPath)) {
            files.filter(Files::isDirectory).forEach(this::registerWatcher);
        }

        var monitorThread = new Thread(this::monitorWatch, getClass().getSimpleName());
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void registerWatcher(Path path) {
        if (path.toString().contains(".git")) {
            return;
        }

        try {
            logger.info("Watching " + path);
            var key = path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            roots.put(key, path);

        } catch (IOException e) {
            logger.error("Failed to register directory watcher on " + path, e);
        }
    }

    public List<MemexNodeUrl> getUpdatedUrls() {
        synchronized (updatedUrls) {
            if (updatedUrls.isEmpty()) {
                return Collections.emptyList();
            }
            var ret = new ArrayList<>(updatedUrls);
            updatedUrls.clear();
            return ret;
        }
    }


    @SneakyThrows
    @SuppressWarnings("unchecked")
    private void monitorWatch() {
        for (;;) {
            var key = watchService.take();

            for (var evt : key.pollEvents()) {
                var kind = evt.kind();

                if (kind == OVERFLOW) {
                    var root = roots.get(key);

                    try (var files = Files.list(root)) {
                        files.forEach(file ->
                            updatedUrls.add(MemexNodeUrl.ofRelativePath(memexRoot, file))
                        );
                    }

                    continue;
                }

                WatchEvent<Path> ev = (WatchEvent<Path>)evt;
                Path root = roots.get(key);
                Path filename = ev.context();
                Path absPath = root.resolve(filename);


                if (kind == ENTRY_CREATE && Files.isDirectory(absPath)) {
                    registerWatcher(absPath);
                }

                MemexNodeUrl url = MemexNodeUrl.ofRelativePath(memexRoot, absPath);
                synchronized (updatedUrls) {
                    updatedUrls.add(url);
                }

            }

            boolean valid = key.reset();
            if (!valid) {
                logger.info("Deregistering key for " + roots.get(key));
                roots.remove(key);
            }
        }
    }
}
