package nu.marginalia.control.actor.monitor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Singleton
public class ServiceHeartbeatMonitorActor extends RecordActorPrototype {

    private static final Logger logger = LoggerFactory.getLogger(ServiceHeartbeatMonitorActor.class);
    private final HikariDataSource dataSource;

    public record Initial() implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Monitor() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial i -> new Monitor();
            case Monitor m -> {
                for (;;) {
                    TimeUnit.SECONDS.sleep(10);
                    pruneDeadServices();
                }
            }
            default -> new Error();
        };
    }

    private void pruneDeadServices() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            // Delete heartbeats that haven't been seen in 10 days
            stmt.execute("""
                    DELETE FROM SERVICE_HEARTBEAT
                    WHERE TIMESTAMPDIFF(SECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) > 10*24*3600
                    """);
        }
        catch (SQLException ex) {
            logger.warn("Failed to prune dead services", ex);
        }
    }

    @Inject
    public ServiceHeartbeatMonitorActor(Gson gson,
                                        HikariDataSource dataSource) {
        super(gson);
        this.dataSource = dataSource;
    }

    @Override
    public String describe() {
        return "Periodically cleans up dead services from the database";
    }

}
