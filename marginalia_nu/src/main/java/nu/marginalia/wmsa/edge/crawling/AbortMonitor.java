package nu.marginalia.wmsa.edge.crawling;


import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class AbortMonitor {
    private volatile boolean abort = false;
    private static volatile AbortMonitor instance = null;
    private static final Logger logger = LoggerFactory.getLogger(AbortMonitor.class);

    public static AbortMonitor getInstance() {
        if (instance == null) {
            synchronized (AbortMonitor.class) {
                if (instance == null) {
                    instance = new AbortMonitor();
                    new Thread(instance::run, "AbortMon").start();
                }
            }
        }
        return instance;
    }

    private AbortMonitor() {
    }

    @SneakyThrows
    public void run() {
        for (;;) {
            Thread.sleep(1000);
            if (Files.exists(Path.of("/tmp/stop"))) {
                logger.warn("Abort file found");
                abort = true;
                Files.delete(Path.of("/tmp/stop"));
            }
        }
    }

    public boolean isAlive() {
        return !abort;
    }
}
