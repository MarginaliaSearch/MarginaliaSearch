package nu.marginalia.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtil {
    private static final int TEST_PORT_BASE = 6000;
    private static final int TEST_PORT_RANGE = 2000;

    public static int getPort() {
        return TEST_PORT_BASE + (int)(TEST_PORT_RANGE * Math.random());
    }
    private final static Logger logger = LoggerFactory.getLogger(TestUtil.class);

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

    @SneakyThrows
    public static void evalScript(HikariDataSource hds, String scriptFile) {

        try (var conn = hds.getConnection()) {

            logger.info("Running script {}", scriptFile);
            try (var scriptStream = ClassLoader.getSystemResourceAsStream(scriptFile);
                 var stmt = conn.createStatement()) {
                for (String s : new String(scriptStream.readAllBytes()).split(";")) {
                    if (!s.isBlank()) {
                        try {
                            Assertions.assertTrue(stmt.executeUpdate(s) >= 0);
                        } catch (Exception ex) {
                            logger.error("Failed to execute\n{}" + s, ex);
                        }

                    }
                }
            }
        }
    }


}
