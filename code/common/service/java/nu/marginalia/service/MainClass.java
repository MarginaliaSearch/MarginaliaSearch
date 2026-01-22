package nu.marginalia.service;

import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.module.ServiceConfigurationModule;
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

        ConfigLoader.loadConfig(
                ConfigLoader.getConfigPath(id.serviceName, ServiceConfigurationModule.getNode())
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
        JvmMetrics.builder().register();
    }

    /** Ensure that the services boot in the correct order, so that the control service
     * has the opportunity to migrate the database before the other services attempt to access it
     * on first boot.
     */
    protected static void orchestrateBoot(ServiceRegistryIf serviceRegistry,
                                          ServiceConfiguration configuration) {

        if (configuration.serviceId() != ServiceId.Control) {
            // If this is not the control service, we need to wait for the control service to boot
            try {
                serviceRegistry.waitForFirstBoot();
            }
            catch (InterruptedException e) {
                // Fail hard here, there is no meaningful recovery
                System.err.println("Interrupted while waiting for control service to boot");
                System.exit(1);
            }
        }
        else {
            // This is the control service, so we need to declare that we have booted successfully
            serviceRegistry.declareFirstBoot();
        }
    }
}
