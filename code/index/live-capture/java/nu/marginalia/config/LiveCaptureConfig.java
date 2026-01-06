package nu.marginalia.config;

import com.google.inject.Inject;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class LiveCaptureConfig {

    private final boolean enableLiveCapture;
    private static final Logger logger = LoggerFactory.getLogger(LiveCaptureConfig.class);

    @Inject
    public LiveCaptureConfig(NodeConfigurationService nodeConfigurationService,
                                  ServiceConfiguration serviceConfiguration) {
        int node = serviceConfiguration.node();

        boolean isEnabled = false;

        try {
            NodeConfiguration nodeConfig = nodeConfigurationService.get(node);
            isEnabled = (nodeConfig.profile() == NodeProfile.REALTIME);
        } catch (SQLException e) {
            logger.error("Failed to read node configuration, disabling live capture");
        }

        enableLiveCapture = isEnabled;
    }

    public LiveCaptureConfig(boolean enabled) {
        enableLiveCapture = enabled;
    }

    public boolean isEnabled() {
        return enableLiveCapture;
    }
}
