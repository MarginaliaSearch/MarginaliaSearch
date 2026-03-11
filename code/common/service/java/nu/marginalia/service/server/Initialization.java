package nu.marginalia.service.server;

import com.google.inject.Singleton;
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
    private final List<NamedCallback> callbacks = new ArrayList<>();

    private record NamedCallback(String name, Runnable action) {}

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

        for (NamedCallback callback : callbacks) {
            long start = System.currentTimeMillis();
            callback.action().run();
            logger.info("Callback {} completed in {}ms", callback.name(), System.currentTimeMillis() - start);
        }
        callbacks.clear();
    }

    public void addCallback(String name, Runnable callback) {
        boolean runNow;

        synchronized (this) {
            if (!initialized) {
                callbacks.add(new NamedCallback(name, callback));
                runNow = false;
            } else {
                runNow = true;
            }
        }

        if (runNow) {
            long start = System.currentTimeMillis();
            callback.run();
            logger.info("Initialization callback: {}={}ms", name, System.currentTimeMillis() - start);
        }
    }

    public boolean isReady() {
        synchronized (this) {
            return initialized;
        }
    }

    public boolean waitReady() {
        try {
            synchronized (this) {
                while (!initialized) {
                    wait();
                }
                return true;
            }
        }
        catch (InterruptedException ex) {
            throw new RuntimeException("Interrupted while waiting for initialization", ex);
        }
    }
}
