package nu.marginalia;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import java.util.Objects;
import java.util.UUID;

public class ProcessConfigurationModule extends AbstractModule {
    private final String processName;

    public ProcessConfigurationModule(String processName) {
        this.processName = processName;
    }

    public void configure() {
        bind(Integer.class).annotatedWith(Names.named("wmsa-system-node")).toInstance(getNode());
        bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration(processName, getNode(), UUID.randomUUID()));
    }

    private int getNode() {

        String nodeEnv = System.getenv("WMSA_PROCESS_NODE");

        if (null == nodeEnv) {
            // fallback logic, try to inherit from parent
            nodeEnv = System.getenv("WMSA_SERVICE_NODE");
        }

        //
        if (null == nodeEnv) {
            throw new IllegalStateException("Either WMSA_PROCESS_NODE or WMSA_SERVICE_NODE must be set, indicating the node affinity of the process!");
        }

        return Integer.parseInt(nodeEnv);
    }

}
