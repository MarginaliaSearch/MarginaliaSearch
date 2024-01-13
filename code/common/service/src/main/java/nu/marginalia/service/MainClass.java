package nu.marginalia.service;

import io.prometheus.client.hotspot.DefaultExports;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.client.exception.NetworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Each main class of a service should extend this class.
 *  They must also invoke init() in their main method.
 */
public abstract class MainClass {
    private final Logger logger = LoggerFactory.getLogger(MainClass.class);

    static {
        // Load global config ASAP
        ConfigLoader.loadConfig(
                ConfigLoader.getConfigPath("system")
        );
    }

    public MainClass() {
        RxJavaPlugins.setErrorHandler(this::handleError);
    }

    protected void handleError(Throwable ex) {
        if (ex instanceof UndeliverableException) {
            ex = ex.getCause();
        }

        if (ex instanceof SocketTimeoutException) {
            logger.warn("SocketTimeoutException");
        }
        else if (ex instanceof UnknownHostException) {
            logger.warn("UnknownHostException");
        }
        else if (ex instanceof NetworkException) {
            logger.warn("NetworkException", ex);
        }
        else {
            logger.error("Uncaught exception", ex);
        }
    }


    protected static void init(ServiceId id, String... args) {
        System.setProperty("log4j2.isThreadContextMapInheritable", "true");
        System.setProperty("isThreadContextMapInheritable", "true");
        System.setProperty("service-name", id.name);

        ConfigLoader.loadConfig(
                ConfigLoader.getConfigPath(id.name)
        );

        initJdbc();
        initPrometheus();
    }

    private static void initJdbc() {
        // This looks weird, but it's just for running the static block
        // in the driver class so that it registers itself

        new org.mariadb.jdbc.Driver();
    }

    private static void initPrometheus() {
        DefaultExports.initialize();
    }

}
