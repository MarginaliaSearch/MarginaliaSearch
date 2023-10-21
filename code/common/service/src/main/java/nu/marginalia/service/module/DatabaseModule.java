package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.service.ServiceHomeNotConfiguredException;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

public class DatabaseModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseModule.class);

    private static final String DB_USER_KEY = "db.user";
    private static final String DB_PASS_KEY = "db.pass";
    private static final String DB_CONN_KEY = "db.conn";

    private final Properties dbProperties;

    public DatabaseModule() {
        new Driver();

        dbProperties = loadDbProperties();
    }

    private Properties loadDbProperties() {
        Path propDir = getHomePath().resolve("conf/db.properties");
        if (!Files.isRegularFile(propDir)) {
            throw new IllegalStateException("Database properties file " + propDir + " does not exist");
        }

        try (var is = new FileInputStream(propDir.toFile())) {
            var props = new Properties();
            props.load(is);

            if (!props.containsKey(DB_USER_KEY)) throw new IllegalStateException(propDir + " missing required attribute " + DB_USER_KEY);
            if (!props.containsKey(DB_PASS_KEY)) throw new IllegalStateException(propDir + " missing required attribute " + DB_PASS_KEY);
            if (!props.containsKey(DB_CONN_KEY)) throw new IllegalStateException(propDir + " missing required attribute " + DB_CONN_KEY);

            return props;
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    public static Path getHomePath() {
        var retStr = Optional.ofNullable(System.getenv("WMSA_HOME")).orElse("/var/lib/wmsa");

        var ret = Path.of(retStr);
        if (!Files.isDirectory(ret)) {
            throw new ServiceHomeNotConfiguredException("Could not find WMSA_HOME, either set environment variable or ensure /var/lib/wmsa exists");
        }
        return ret;
    }


    @SneakyThrows
    @Singleton
    @Provides
    public HikariDataSource provideConnection() {
        return getMariaDB();
    }

    @SneakyThrows
    private HikariDataSource getMariaDB() {
        var connStr = System.getProperty("db.overrideJdbc", dbProperties.getProperty(DB_CONN_KEY));

        try {
            HikariConfig config = new HikariConfig();


            config.setJdbcUrl(connStr);
            config.setUsername(dbProperties.getProperty(DB_USER_KEY));
            config.setPassword(dbProperties.getProperty(DB_PASS_KEY));

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            config.setMaximumPoolSize(5);
            config.setMinimumIdle(2);

            config.setMaxLifetime(Duration.ofMinutes(9).toMillis());

            return new HikariDataSource(config);
        }
        finally {
            logger.info("Created HikariPool for {}", connStr);
        }
    }

}
