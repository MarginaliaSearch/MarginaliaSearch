package nu.marginalia;

import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/** The IndexLocations class is responsible for knowledge about the locations
 * of various important system paths.  The methods take a FileStorageService,
 * as these paths are node-dependent.
 */
public class IndexLocations {

    private static final Logger logger = LoggerFactory.getLogger(IndexLocations.class);
    /** Return the path to the current link database */
    public static Path getLinkdbLivePath(FileStorageService fileStorage) {
        return getStorage(fileStorage, FileStorageBaseType.CURRENT, "ldbr");
    }

    /** Return the path to the next link database */
    public static Path getLinkdbWritePath(FileStorageService fileStorage) {
        return getStorage(fileStorage, FileStorageBaseType.CURRENT, "ldbw");
    }

    /** Return the path to the current live index */
    public static Path getCurrentIndex(FileStorageService fileStorage) {
        return getStorage(fileStorage, FileStorageBaseType.CURRENT, "ir");
    }

    /** Return the path to the designated index construction area */
    public static Path getIndexConstructionArea(FileStorageService fileStorage) {
        return getStorage(fileStorage, FileStorageBaseType.CURRENT, "iw");
    }

    /** Return the path to the search sets */
    public static Path getSearchSetsPath(FileStorageService fileStorage) {
        return getStorage(fileStorage, FileStorageBaseType.CURRENT, "ss");
    }

    private static Path getStorage(FileStorageService service, FileStorageBaseType baseType, String pathPart) {
        try {
            var base = service.getStorageBase(baseType);
            if (base == null) {
                throw new IllegalStateException("File storage base " + baseType + " is not configured!");
            }

            // Ensure the directory exists
            Path ret = base.asPath().resolve(pathPart);
            if (!Files.exists(ret)) {
                logger.info("Creating system directory {}", ret);

                Files.createDirectories(ret);
            }

            return ret;
        }
        catch (SQLException | IOException ex) {
            throw new IllegalStateException("Error fetching storage " + baseType + " / " + pathPart, ex);
        }
    }

}
