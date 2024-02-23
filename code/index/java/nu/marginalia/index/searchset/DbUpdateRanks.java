package nu.marginalia.index.searchset;

import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;

@Singleton
public class DbUpdateRanks {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HikariDataSource dataSource;

    @Inject
    public DbUpdateRanks(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void execute(Int2IntOpenHashMap ranks) {
        try (var conn = dataSource.getConnection();
             var resetStmt = conn.createStatement();
             var updateStmt = conn.prepareStatement("UPDATE EC_DOMAIN SET RANK=? WHERE ID=?")) {

            resetStmt.executeUpdate("UPDATE EC_DOMAIN SET RANK=1");

            int rankMax = ranks.size();
            int i = 0;

            for (var iter = ranks.int2IntEntrySet().fastIterator(); iter.hasNext(); i++) {
                var entry = iter.next();

                updateStmt.setDouble(1,  entry.getIntValue() / (double) rankMax);
                updateStmt.setInt(2, entry.getIntKey());
                updateStmt.addBatch();

                if (i > 100) {
                    updateStmt.executeBatch();
                    i = 0;
                }
            }
            if (i > 0) {
                updateStmt.executeBatch();
            }
        }
        catch (SQLException ex) {
            logger.info("Failed to update ranks");
        }
    }


}
