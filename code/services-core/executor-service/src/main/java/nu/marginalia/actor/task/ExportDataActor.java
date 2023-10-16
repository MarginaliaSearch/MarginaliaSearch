package nu.marginalia.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
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
public class ExportDataActor extends AbstractActorPrototype {

    private static final String blacklistFilename = "blacklist.csv.gz";
    private static final String domainsFilename = "domains.csv.gz";
    private static final String linkGraphFilename = "linkgraph.csv.gz";


    // STATES
    public static final String INITIAL = "INITIAL";
    public static final String EXPORT_DOMAINS = "EXPORT-DOMAINS";
    public static final String EXPORT_BLACKLIST  = "EXPORT-BLACKLIST";
    public static final String EXPORT_LINK_GRAPH  = "EXPORT-LINK-GRAPH";

    public static final String END = "END";
    private final FileStorageService storageService;
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId storageId = null;
    };

    @Override
    public String describe() {
        return "Export data from the database to a storage area of type EXPORT.";
    }

    @Inject
    public ExportDataActor(ActorStateFactory stateFactory,
                           FileStorageService storageService,
                           HikariDataSource dataSource)
    {
        super(stateFactory);
        this.storageService = storageService;
        this.dataSource = dataSource;
    }

    @ActorState(name = INITIAL,
                next = EXPORT_BLACKLIST,
                description = """
                    Find EXPORT storage area, then transition to EXPORT-BLACKLIST.
                    """)
    public Message init(Integer i) throws Exception {
        var storage = storageService.getStorageByType(FileStorageType.EXPORT);
        if (storage == null) error("Bad storage id");

        return new Message().withStorageId(storage.id());
    }

    @ActorState(name = EXPORT_BLACKLIST,
                next = EXPORT_DOMAINS,
                resume = ActorResumeBehavior.ERROR,
                description = """
                        Export the blacklist from the database to the EXPORT storage area.
                        """
    )
    public Message exportBlacklist(Message message) throws Exception {
        var storage = storageService.getStorage(message.storageId);
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
            error("Failed to export blacklist");
        }
        finally {
            Files.deleteIfExists(tmpFile);
        }

        return message;
    }

    @ActorState(
            name = EXPORT_DOMAINS,
            next = EXPORT_LINK_GRAPH,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Export known domains to the EXPORT storage area.
                    """
    )
    public Message exportDomains(Message message) throws Exception {
        var storage = storageService.getStorage(message.storageId);
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
            error("Failed to export domains");
        }
        finally {
            Files.deleteIfExists(tmpFile);
        }

        return message;
    }

    @ActorState(
            name = EXPORT_LINK_GRAPH,
            next = END,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Export known domains to the EXPORT storage area.
                    """
    )
    public Message exportLinkGraph(Message message) throws Exception {
        var storage = storageService.getStorage(message.storageId);
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
            error("Failed to export link graph");
        }
        finally {
            Files.deleteIfExists(tmpFile);
        }

        return message;
    }

}
