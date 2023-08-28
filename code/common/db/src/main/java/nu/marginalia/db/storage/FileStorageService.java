package nu.marginalia.db.storage;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.storage.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.*;

/** Manages file storage for processes and services
 */
@Singleton
public class FileStorageService {
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    @Inject
    public FileStorageService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<FileStorage> findFileStorageToDelete() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT ID FROM FILE_STORAGE WHERE DO_PURGE LIMIT 1
                """)) {
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(getStorage(new FileStorageId(rs.getLong(1))));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** @return the storage base with the given id, or null if it does not exist */
    public FileStorageBase getStorageBase(FileStorageBaseId type) throws SQLException  {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT ID, NAME, PATH, TYPE, PERMIT_TEMP
                     FROM FILE_STORAGE_BASE WHERE ID = ?
                     """)) {
            stmt.setLong(1, type.id());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new FileStorageBase(
                            new FileStorageBaseId(rs.getLong(1)),
                            FileStorageBaseType.valueOf(rs.getString(4)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getBoolean(5)
                    );
                }
            }
        }
        return null;
    }

    public void synchronizeStorageManifests(FileStorageBase base) {
        Set<String> ignoredPaths = new HashSet<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT PATH FROM FILE_STORAGE WHERE BASE_ID = ?
                """)) {
            stmt.setLong(1, base.id().id());
            var rs = stmt.executeQuery();
            while (rs.next()) {
                ignoredPaths.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        File basePathFile = Path.of(base.path()).toFile();
        File[] files = basePathFile.listFiles(pathname -> pathname.isDirectory() && !ignoredPaths.contains(pathname.getName()));
        if (files == null) return;
        for (File file : files) {
            var maybeManifest = FileStorageManifest.find(file.toPath());
            if (maybeManifest.isEmpty()) continue;
            var manifest = maybeManifest.get();

            logger.info("Discovered new file storage: " + file.getName() + " (" + manifest.type() + ")");

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("""
                    INSERT INTO FILE_STORAGE(BASE_ID, PATH, TYPE, DESCRIPTION)
                    VALUES (?, ?, ?, ?)
                    """)) {
                stmt.setLong(1, base.id().id());
                stmt.setString(2, file.getName());
                stmt.setString(3, manifest.type().name());
                stmt.setString(4, manifest.description());
                stmt.execute();
                conn.commit();

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public void relateFileStorages(FileStorageId source, FileStorageId target) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                INSERT INTO FILE_STORAGE_RELATION(SOURCE_ID, TARGET_ID) VALUES (?, ?)
                """)) {
            stmt.setLong(1, source.id());
            stmt.setLong(2, target.id());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<FileStorage> getSourceFromStorage(FileStorage storage) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT SOURCE_ID FROM FILE_STORAGE_RELATION WHERE TARGET_ID = ?
                     """)) {
            stmt.setLong(1, storage.id().id());
            var rs = stmt.executeQuery();
            List<FileStorage> ret = new ArrayList<>();
            while (rs.next()) {
                ret.add(getStorage(new FileStorageId(rs.getLong(1))));
            }
            return ret;
        }
    }

    public List<FileStorage> getTargetFromStorage(FileStorage storage) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT TARGET_ID FROM FILE_STORAGE_RELATION WHERE SOURCE_ID = ?
                     """)) {
            stmt.setLong(1, storage.id().id());
            var rs = stmt.executeQuery();
            List<FileStorage> ret = new ArrayList<>();
            while (rs.next()) {
                ret.add(getStorage(new FileStorageId(rs.getLong(1))));
            }
            return ret;
        }
    }

    /** @return the storage base with the given type, or null if it does not exist */
    public FileStorageBase getStorageBase(FileStorageBaseType type) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT ID, NAME, PATH, TYPE, PERMIT_TEMP
                     FROM FILE_STORAGE_BASE WHERE TYPE = ?
                     """)) {
            stmt.setString(1, type.name());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new FileStorageBase(
                            new FileStorageBaseId(rs.getLong(1)),
                            FileStorageBaseType.valueOf(rs.getString(4)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getBoolean(5)
                    );
                }
            }
        }
        return null;
    }

    public FileStorageBase createStorageBase(String name, Path path, FileStorageBaseType type, boolean permitTemp) throws SQLException, FileNotFoundException {

        if (!Files.exists(path)) {
            throw new FileNotFoundException("Storage base path does not exist: " + path);
        }

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO FILE_STORAGE_BASE(NAME, PATH, TYPE, PERMIT_TEMP)
                     VALUES (?, ?, ?, ?)
                     """)) {
            stmt.setString(1, name);
            stmt.setString(2, path.toString());
            stmt.setString(3, type.name());
            stmt.setBoolean(4, permitTemp);

            int update = stmt.executeUpdate();
            if (update < 0) {
                throw new SQLException("Failed to create storage base");
            }
        }

        return getStorageBase(type);
    }

    /** Allocate a temporary storage of the given type if temporary allocation is permitted */
    public FileStorage allocateTemporaryStorage(FileStorageBase base,
                                                FileStorageType type,
                                                String prefix,
                                                String description) throws IOException, SQLException
    {
        if (!base.permitTemp()) {
            throw new IllegalArgumentException("Temporary storage not permitted in base "  + base.name());
        }

        Path tempDir = Files.createTempDirectory(base.asPath(), prefix,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"))
        );

        String relDir = base.asPath().relativize(tempDir).normalize().toString();

        try (var conn = dataSource.getConnection();
             var insert = conn.prepareStatement("""
                INSERT INTO FILE_STORAGE(PATH, TYPE, DESCRIPTION, BASE_ID)
                VALUES (?, ?, ?, ?)
                """);
             var query = conn.prepareStatement("""
                SELECT ID FROM FILE_STORAGE WHERE PATH = ? AND BASE_ID = ?
                """)
             ) {
            insert.setString(1, relDir);
            insert.setString(2, type.name());
            insert.setString(3, description);
            insert.setLong(4, base.id().id());

            if (insert.executeUpdate() < 1) {
                throw new SQLException("Failed to insert storage");
            }


            query.setString(1, relDir);
            query.setLong(2, base.id().id());
            var rs = query.executeQuery();

            if (rs.next()) {
                var storage = getStorage(new FileStorageId(rs.getLong("ID")));

                // Write a manifest file so we can pick this up later without needing to insert it into DB
                // (e.g. when loading from outside the system)
                var manifest = new FileStorageManifest(type, description);
                manifest.write(storage);

                return storage;
            }

        }

        throw new SQLException("Failed to insert storage");
    }


    /** Allocate permanent storage in base */
    public FileStorage allocatePermanentStorage(FileStorageBase base, String relativePath, FileStorageType type, String description) throws IOException, SQLException {

        Path newDir = base.asPath().resolve(relativePath);

        if (Files.exists(newDir)) {
            throw new IllegalArgumentException("Storage already exists: " + newDir);
        }

        Files.createDirectory(newDir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));

        try (var conn = dataSource.getConnection();
             var update = conn.prepareStatement("""
                INSERT INTO FILE_STORAGE(PATH, TYPE, DESCRIPTION, BASE_ID)
                VALUES (?, ?, ?, ?)
                """);
             var query = conn.prepareStatement("""
                SELECT ID
                FROM FILE_STORAGE WHERE PATH = ? AND BASE_ID = ?
                """)
        ) {
            update.setString(1, relativePath);
            update.setString(2, type.name());
            update.setString(3, description);
            update.setLong(4, base.id().id());

            if (update.executeUpdate() < 1)
                throw new SQLException("Failed to insert storage");

            query.setString(1, relativePath);
            query.setLong(2, base.id().id());
            var rs = query.executeQuery();

            if (rs.next()) {
                return new FileStorage(
                        new FileStorageId(rs.getLong("ID")),
                        base,
                        type,
                        newDir.toString(),
                        description
                );
            }

        }

        throw new SQLException("Failed to insert storage");
    }

    public FileStorage getStorageByType(FileStorageType type) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT PATH, DESCRIPTION, ID, BASE_ID
                     FROM FILE_STORAGE_VIEW WHERE TYPE = ?
                     """)) {
            stmt.setString(1, type.name());

            long storageId;
            long baseId;
            String path;
            String description;

            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    baseId = rs.getLong("BASE_ID");
                    storageId = rs.getLong("ID");
                    path = rs.getString("PATH");
                    description = rs.getString("DESCRIPTION");
                }
                else {
                    return null;
                }

                var base = getStorageBase(new FileStorageBaseId(baseId));

                return new FileStorage(
                        new FileStorageId(storageId),
                        base,
                        type,
                        path,
                        description
                );
            }
        }
    }

    /** @return the storage with the given id, or null if it does not exist */
    public FileStorage getStorage(FileStorageId id) throws SQLException {

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT PATH, TYPE, DESCRIPTION, ID, BASE_ID
                     FROM FILE_STORAGE_VIEW WHERE ID = ?
                     """)) {
            stmt.setLong(1, id.id());

            long storageId;
            long baseId;
            String path;
            String description;
            FileStorageType type;

            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    baseId = rs.getLong("BASE_ID");
                    storageId = rs.getLong("ID");
                    type = FileStorageType.valueOf(rs.getString("TYPE"));
                    path = rs.getString("PATH");
                    description = rs.getString("DESCRIPTION");
                }
                else {
                    return null;
                }

                var base = getStorageBase(new FileStorageBaseId(baseId));

                return new FileStorage(
                        new FileStorageId(storageId),
                        base,
                        type,
                        path,
                        description
                );
            }
        }
    }

    public void removeFileStorage(FileStorageId id) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM FILE_STORAGE WHERE ID = ?
                     """)) {
            stmt.setLong(1, id.id());
            stmt.executeUpdate();
        }
    }

    public List<FileStorage> getEachFileStorage() {
        List<FileStorage> ret = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT PATH, TYPE, DESCRIPTION, ID, BASE_ID
                     FROM FILE_STORAGE_VIEW
                     """)) {

            long storageId;
            long baseId;
            String path;
            String description;
            FileStorageType type;

            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    baseId = rs.getLong("BASE_ID");
                    storageId = rs.getLong("ID");
                    path = rs.getString("PATH");
                    type = FileStorageType.valueOf(rs.getString("TYPE"));
                    description = rs.getString("DESCRIPTION");

                    var base = getStorageBase(new FileStorageBaseId(baseId));

                    ret.add(new FileStorage(
                            new FileStorageId(storageId),
                            base,
                            type,
                            path,
                            description
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ret;
    }
}
