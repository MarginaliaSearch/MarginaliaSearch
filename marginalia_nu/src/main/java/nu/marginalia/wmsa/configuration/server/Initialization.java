package nu.marginalia.wmsa.configuration.server;

import com.google.inject.Singleton;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Initialization {
    boolean initialized;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static Initialization already() {
        Initialization init = new Initialization();
        init.setReady();
        return init;
    }

    public void setReady() {
        synchronized (this) {
            logger.info("Initialized");
            initialized = true;
            notifyAll();
        }

        if (Boolean.getBoolean("go-no-go")) {
            logger.info("Self-test OK");
            System.exit(0);
        }
    }

    public boolean isReady() {
        synchronized (this) {
            return initialized;
        }
    }

    @SneakyThrows
    public boolean waitReady() {
        synchronized (this) {
            while (!initialized) {
                wait();
            }
            return initialized;
        }
    }
}
