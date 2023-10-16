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
import nu.marginalia.storage.model.FileStorageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

@Singleton
public class TruncateLinkDatabase extends AbstractActorPrototype {


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

    @Override
    public String describe() {
        return "Remove all data from the link database.";
    }

    @Inject
    public TruncateLinkDatabase(ActorStateFactory stateFactory,
                                HikariDataSource dataSource)
    {
        super(stateFactory);
        this.dataSource = dataSource;
    }

    @ActorState(name = INITIAL,
                next = FLUSH_DATABASE,
                description = """
                    Initial stage
                    """)
    public void init(Integer i) throws Exception {

    }

    @ActorState(name = FLUSH_DATABASE,
                next = END,
                resume = ActorResumeBehavior.ERROR,
                description = """
                        Truncate the domain and link tables.
                        """
    )
    public void flushDatabase() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement())
        {
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
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
