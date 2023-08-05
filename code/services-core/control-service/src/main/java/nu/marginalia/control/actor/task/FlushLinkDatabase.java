package nu.marginalia.control.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.zip.GZIPOutputStream;

@Singleton
public class FlushLinkDatabase extends AbstractStateGraph {


    // STATES
    public static final String INITIAL = "INITIAL";
    public static final String FLUSH_DATABASE = "FLUSH_DATABASE";

    public static final String END = "END";
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId storageId = null;
    };

    @Inject
    public FlushLinkDatabase(StateFactory stateFactory,
                             HikariDataSource dataSource)
    {
        super(stateFactory);
        this.dataSource = dataSource;
    }

    @GraphState(name = INITIAL,
                next = FLUSH_DATABASE,
                description = """
                    Initial stage
                    """)
    public void init(Integer i) throws Exception {

    }

    @GraphState(name = FLUSH_DATABASE,
                next = END,
                resume = ResumeBehavior.ERROR,
                description = """
                        Truncate the domain and link tables.
                        """
    )
    public void exportBlacklist() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement())
        {
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
            stmt.executeUpdate("TRUNCATE TABLE EC_PAGE_DATA");
            stmt.executeUpdate("TRUNCATE TABLE EC_URL");
            stmt.executeUpdate("TRUNCATE TABLE EC_DOMAIN_LINK");
            stmt.executeUpdate("TRUNCATE TABLE DOMAIN_METADATA");
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
        }
        catch (SQLException ex) {
            logger.error("Failed to truncate tables", ex);
            error("Failed to truncate tables");
        }
    }


}
