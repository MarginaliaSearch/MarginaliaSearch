package nu.marginalia.crawlspec;

import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageType;

import java.nio.file.Path;

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
}
