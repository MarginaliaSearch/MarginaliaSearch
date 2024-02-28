package nu.marginalia.service.server;

import com.google.inject.Singleton;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** A class for delayed initialization of services.
 * <p></p>
 * This is useful for tasks that need to be performed after the service has been
 * fully initialized, such as registering with a service registry.
 */
@Singleton
public class Initialization {
    private boolean initialized;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<Runnable> callbacks = new ArrayList<>();

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

        callbacks.forEach(Runnable::run);
        callbacks.clear();
    }

    public void addCallback(Runnable callback) {
        boolean runNow;

        synchronized (this) {
            if (!initialized) {
                callbacks.add(callback);
                runNow = false;
            } else {
                runNow = true;
            }
        }

        if (runNow) {
            callback.run();
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
            return true;
        }
    }
}
