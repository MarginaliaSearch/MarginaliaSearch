package nu.marginalia.ping.model;

import java.sql.Connection;
import java.sql.SQLException;

public interface WritableModel {
     void write(Connection connection) throws SQLException;
}
