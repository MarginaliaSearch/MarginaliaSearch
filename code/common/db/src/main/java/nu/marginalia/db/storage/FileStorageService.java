package nu.marginalia.db.storage;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.storage.model.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;

/** Manages file storage for processes and services
 */
@Singleton
public class FileStorageService {
    private final HikariDataSource dataSource;

    @Inject
    public FileStorageService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @return the storage base with the given id, or null if it does not exist */
    public FileStorageBase getStorageBase(FileStorageBaseId type) throws SQLException  {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT ID, NAME, PATH, TYPE, MUST_CLEAN, PERMIT_TEMP
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
                            rs.getBoolean(5),
                            rs.getBoolean(6)
                    );
                }
            }
        }
        return null;
    }

    /** @return the storage base with the given type, or null if it does not exist */
    public FileStorageBase getStorageBase(FileStorageBaseType type) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT ID, NAME, PATH, TYPE, MUST_CLEAN, PERMIT_TEMP
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
                            rs.getBoolean(5),
                            rs.getBoolean(6)
                    );
                }
            }
        }
        return null;
    }

    public FileStorageBase createStorageBase(String name, Path path, FileStorageBaseType type, boolean mustClean, boolean permitTemp) throws SQLException, FileNotFoundException {

        if (!Files.exists(path)) {
            throw new FileNotFoundException("Storage base path does not exist: " + path);
        }

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO FILE_STORAGE_BASE(NAME, PATH, TYPE, MUST_CLEAN, PERMIT_TEMP)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            stmt.setString(1, name);
            stmt.setString(2, path.toString());
            stmt.setString(3, type.name());
            stmt.setBoolean(4, mustClean);
            stmt.setBoolean(5, permitTemp);

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

        try (var conn = dataSource.getConnection();
             var update = conn.prepareStatement("""
                INSERT INTO FILE_STORAGE(PATH, TYPE, DESCRIPTION, BASE_ID)
                VALUES (?, ?, ?, ?)
                """);
             var query = conn.prepareStatement("""
                SELECT ID FROM FILE_STORAGE WHERE PATH = ? AND BASE_ID = ?
                """)
             ) {
            update.setString(1, tempDir.toString());
            update.setString(2, type.name());
            update.setString(3, description);
            update.setLong(4, base.id().id());

            if (update.executeUpdate() < 1)
                throw new SQLException("Failed to insert storage");

            query.setString(1, tempDir.toString());
            query.setLong(2, base.id().id());
            var rs = query.executeQuery();

            if (rs.next()) {
                return new FileStorage(
                        new FileStorageId(rs.getLong("ID")),
                        base,
                        type,
                        tempDir.toString(),
                        description
                );
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

}
