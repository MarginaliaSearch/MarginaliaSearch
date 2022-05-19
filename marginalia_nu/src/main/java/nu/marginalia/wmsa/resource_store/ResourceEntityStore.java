package nu.marginalia.wmsa.resource_store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.name.Named;
import io.prometheus.client.Counter;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


public class ResourceEntityStore {
    private final Map<String, RenderedResource> resources = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Path dataPath;
    private final Gson gson = new GsonBuilder().create();
    private final Base64.Encoder b64encoder = Base64.getEncoder();

    private final static Counter wmsa_resource_store_count
            = Counter.build("wmsa_resource_store_count", "number of items in the resource store")
            .register();
    private final static Counter wmsa_resource_store_eviction_count
            = Counter.build("wmsa_resource_store_eviction_count", "evicted items")
            .register();

    @Inject
    public ResourceEntityStore(@Named("data-path") Path dataPath) {
        this.dataPath = dataPath;

        Schedulers.io().scheduleDirect(() -> loadResourcesFromDisk(dataPath));
        Schedulers.io().schedulePeriodicallyDirect(() -> purgeFileSystem(dataPath), 1, 1, TimeUnit.HOURS);
    }

    public ResourceEntityStore(@Named("data-path") Path dataPath, boolean immediate) {
        this.dataPath = dataPath;

        loadResourcesFromDisk(dataPath);
    }

    public ResourceEntityStore() {
        this.dataPath = null;
    }

    public RenderedResource getResource(String domain, String resource) {
        Lock readLock = lock.readLock();
        try {
            readLock.lock();
            return resources.get(getKey(domain, resource));
        }
        finally {
            readLock.unlock();
        }
    }

    public void putResource(String domain, String resource, RenderedResource data) {
        RenderedResource oldResource = loadResource(domain, resource, data);

        wmsa_resource_store_count.inc();
        if (dataPath != null) {
            Path domainPath = dataPath.resolve(domain);
            if (!domainPath.toFile().isDirectory()) {
                domainPath.toFile().mkdir();
            }

            if (oldResource != null) {
                try {
                    Path oldResourcePath = domainPath.resolve(oldResource.diskFileName());
                    oldResourcePath.toFile().delete();
                }
                catch (Exception ex) {
                    logger.error("Failed to remove old resource {}/{}", domain, oldResource.diskFileName());
                }
            }

            Path resourcePath = domainPath.resolve(data.diskFileName());
            try {
                Files.writeString(resourcePath, gson.toJson(data));
            } catch (IOException e) {
                logger.error("Failed to write resource {}/{}", domain, resource);
                logger.error("Exception", e);
            }


        }
    }

    @Nullable
    private RenderedResource loadResource(String domain, String resource, RenderedResource data) {
        Lock writeLock = lock.writeLock();
        RenderedResource oldResource;
        try {
            writeLock.lock();
            oldResource = resources.put(getKey(domain, resource), data);
        }
        finally {
            writeLock.unlock();
        }
        return oldResource;
    }

    private String getKey(String domain, String resource) {
        return domain + "/" + resource;
    }


    public void reapStaleResources() {
        Lock writeLock = lock.writeLock();
        try {
            writeLock.lock();
            List<String> expiredResources = resources.entrySet().stream()
                    .filter(entry -> entry.getValue().isExpired())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            for (String resource : expiredResources) {
                logger.info("Reaping expired resource \"{}\"", resource);
                var res = resources.remove(resource);
                wmsa_resource_store_eviction_count.inc();

                if (dataPath != null) {
                    File resourceFile = dataPath.resolve(res.diskFileName()).toFile();
                    if (resourceFile.exists()) {
                        resourceFile.delete();
                    }
                }
            }
        }
        finally {
            writeLock.unlock();
        }
    }

    public int numResources() {
        Lock readLock = lock.readLock();
        try {
            readLock.lock();
            return resources.size();
        }
        finally {
            readLock.unlock();
        }
    }

    public long resourceSize() {
        Lock readLock = lock.readLock();
        try {
            readLock.lock();
            return resources.values().stream().mapToLong(RenderedResource::size).sum();
        }
        finally {
            readLock.unlock();
        }
    }

    public void loadResourcesFromDisk(Path dataPath) {
        File dataDir = dataPath.toFile();

        for (var dir : dataDir.listFiles()) {
            if (!dir.isDirectory()) {
                logger.warn("Junk file {} in data directory", dir);
            }
            else {
                for (var file : dir.listFiles()) {
                    try {
                        loadFromFile(dir.getName(), file);
                    }
                    catch (Exception ex) {
                        logger.error("Failed to load file {}", file);
                        logger.error("Failed to load resource from disk", ex);
                    }
                }
            }
        }
    }

    public void purgeFileSystem(Path dataPath) {
        File dataDir = dataPath.toFile();

        for (var dir : dataDir.listFiles()) {
            if (!dir.isDirectory()) {
                logger.warn("Junk file {} in data directory", dir);
            }
            else {
                for (var file : dir.listFiles()) {
                    try {
                        purgeFile(file);
                    }
                    catch (Exception ex) {
                        logger.error("Failed to purge resource from disk", ex);
                    }
                }
            }
        }
    }

    private void purgeFile(File file) throws IOException {
        String json = Files.readString(file.toPath(), Charset.defaultCharset());
        var resource = gson.fromJson(json, RenderedResource.class);

        if (resource.isExpired()) {
            logger.info("Deleting expired resource {}", file);

            file.delete();
        }

    }


    private void loadFromFile(String domain, File file) {
        try {
            String json = Files.readString(file.toPath(), Charset.defaultCharset());
            var resource = gson.fromJson(json, RenderedResource.class);

            if (resource.isExpired() || resources.containsKey(getKey(domain, resource.getFilename()))) {
                logger.info("Deleting expired resource {}", file);

                file.delete();
            }
            else {
                logger.info("Re-loading resource {}", file);
                loadResource(domain, resource.getFilename(), resource);
            }
        } catch (IOException e) {
            logger.error("Could not read file {}", file.toString());
        }
    }

}
