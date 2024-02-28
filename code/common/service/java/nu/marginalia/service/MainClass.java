package nu.marginalia.service;

import io.prometheus.client.hotspot.DefaultExports;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    }

    protected static void init(ServiceId id, String... args) {
        System.setProperty("log4j2.isThreadContextMapInheritable", "true");
        System.setProperty("isThreadContextMapInheritable", "true");
        System.setProperty("service-name", id.serviceName);

        ConfigLoader.loadConfig(
                ConfigLoader.getConfigPath(id.serviceName)
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
