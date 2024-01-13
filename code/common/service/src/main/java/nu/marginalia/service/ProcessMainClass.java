package nu.marginalia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProcessMainClass {
    private static final Logger logger = LoggerFactory.getLogger(ProcessMainClass.class);

    static {
        // Load global config ASAP
        ConfigLoader.loadConfig(
                ConfigLoader.getConfigPath("system")
        );
    }

    public ProcessMainClass() {
        new org.mariadb.jdbc.Driver();
    }

}
