package nu.marginalia.ping.model;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

public interface WritableModel {
     void write(Connection connection) throws SQLException;
     @Nullable
     default Instant nextUpdateTime() {
            return null;
     }
}
