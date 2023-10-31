package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.GZIPOutputStream;

@Singleton
public class ExportDataActor extends RecordActorPrototype {

    private static final String blacklistFilename = "blacklist.csv.gz";
    private static final String domainsFilename = "domains.csv.gz";
    private static final String linkGraphFilename = "linkgraph.csv.gz";

    private final FileStorageService storageService;
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId storageId = null;
    };

    public record Export() implements ActorStep {}
    public record ExportBlacklist(FileStorageId fid) implements ActorStep {}
    public record ExportDomains(FileStorageId fid) implements ActorStep {}
    public record ExportLinkGraph(FileStorageId fid) implements ActorStep {}
    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export() -> {
                var storage = storageService.getStorageByType(FileStorageType.EXPORT);

                if (storage == null) yield new Error("Bad storage id");
                yield new ExportBlacklist(storage.id());
            }
            case ExportBlacklist(FileStorageId fid) -> {
                var storage = storageService.getStorage(fid);
                var tmpFile = Files.createTempFile(storage.asPath(), "export", ".csv.gz",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

                try (var bw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))));
                     var conn = dataSource.getConnection();
                     var stmt = conn.prepareStatement("SELECT URL_DOMAIN FROM EC_DOMAIN_BLACKLIST");
                )
                {
                    stmt.setFetchSize(1000);
                    var rs = stmt.executeQuery();
                    while (rs.next()) {
                        bw.write(rs.getString(1));
                        bw.write("\n");
                    }
                    Files.move(tmpFile, storage.asPath().resolve(blacklistFilename), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (Exception ex) {
                    logger.error("Failed to export blacklist", ex);
                    yield new Error("Failed to export blacklist");
                }
                finally {
                    Files.deleteIfExists(tmpFile);
                }

                yield new ExportDomains(fid);
            }
            case ExportDomains(FileStorageId fid) -> {
                var storage = storageService.getStorage(fid);
                var tmpFile = Files.createTempFile(storage.asPath(), "export", ".csv.gz",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

                try (var bw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))));
                     var conn = dataSource.getConnection();
                     var stmt = conn.prepareStatement("SELECT DOMAIN_NAME, ID, INDEXED, STATE FROM EC_DOMAIN");
                )
                {
                    stmt.setFetchSize(1000);
                    var rs = stmt.executeQuery();
                    while (rs.next()) {
                        bw.write(rs.getString("DOMAIN_NAME"));
                        bw.write(",");
                        bw.write(rs.getString("ID"));
                        bw.write(",");
                        bw.write(rs.getString("INDEXED"));
                        bw.write(",");
                        bw.write(rs.getString("STATE"));
                        bw.write("\n");
                    }
                    Files.move(tmpFile, storage.asPath().resolve(domainsFilename), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (Exception ex) {
                    logger.error("Failed to export domains", ex);
                    yield new Error("Failed to export domains");
                }
                finally {
                    Files.deleteIfExists(tmpFile);
                }

                yield new ExportLinkGraph(fid);
            }
            case ExportLinkGraph(FileStorageId fid) -> {
                var storage = storageService.getStorage(fid);
                var tmpFile = Files.createTempFile(storage.asPath(), "export", ".csv.gz",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

                try (var bw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))));
                     var conn = dataSource.getConnection();
                     var stmt = conn.prepareStatement("SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK");
                )
                {
                    stmt.setFetchSize(1000);
                    var rs = stmt.executeQuery();
                    while (rs.next()) {
                        bw.write(rs.getString("SOURCE_DOMAIN_ID"));
                        bw.write(",");
                        bw.write(rs.getString("DEST_DOMAIN_ID"));
                        bw.write("\n");
                    }
                    Files.move(tmpFile, storage.asPath().resolve(linkGraphFilename), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (Exception ex) {
                    logger.error("Failed to export link graph", ex);
                    yield new Error("Failed to export link graph");
                }
                finally {
                    Files.deleteIfExists(tmpFile);
                }

                yield new End();
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Export data from the database to a storage area of type EXPORT.";
    }

    @Inject
    public ExportDataActor(Gson gson,
                           FileStorageService storageService,
                           HikariDataSource dataSource)
    {
        super(gson);
        this.storageService = storageService;
        this.dataSource = dataSource;
    }

}
