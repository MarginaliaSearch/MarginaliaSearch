package nu.marginalia.crawlspec;

import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CrawlSpecFileNames {
    public static Path resolve(Path base) {
        return base.resolve("crawl-spec.parquet");
    }

    public static Path resolve(FileStorage storage) {
        if (storage.type() != FileStorageType.CRAWL_SPEC)
            throw new IllegalArgumentException("Provided file storage is of unexpected type " +
                    storage.type() + ", expected CRAWL_SPEC");

        return resolve(storage.asPath());
    }

    public static List<Path> resolve(List<FileStorage> storageList) {
        List<Path> ret = new ArrayList<>();
        for (var storage : storageList) {
            if (storage.type() != FileStorageType.CRAWL_SPEC)
                throw new IllegalArgumentException("Provided file storage is of unexpected type " +
                        storage.type() + ", expected CRAWL_SPEC");
            ret.add(resolve(storage));
        }

        return ret;
    }
}
