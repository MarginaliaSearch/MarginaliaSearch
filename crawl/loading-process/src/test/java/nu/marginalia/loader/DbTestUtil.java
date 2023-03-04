package nu.marginalia.loader;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbTestUtil {
    private static final int TEST_PORT_BASE = 6000;
    private static final int TEST_PORT_RANGE = 2000;

    private final static Logger logger = LoggerFactory.getLogger(DbTestUtil.class);

    public static int getPort() {
        return TEST_PORT_BASE + (int)(TEST_PORT_RANGE * Math.random());
    }

    @SneakyThrows
    public static HikariDataSource getConnection() {
        return getConnection("jdbc:mysql://localhost:3306/WMSA_test");
    }

    @SneakyThrows
    public static HikariDataSource getConnection(String connString) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connString);
        config.setUsername("wmsa");
        config.setPassword("wmsa");
        config.setMaximumPoolSize(16);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

}
