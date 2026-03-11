package nu.marginalia.control;

import nu.marginalia.test.AbstractDockerServiceTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Starts the control-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class ControlServiceDockerTest extends AbstractDockerServiceTest {

    public ControlServiceDockerTest() {
        super("control-service", FirstBoot.EXPECT_SERVICE_CREATES);
    }

    @Test
    public void firstBootZnodeCreatedByService() throws Exception {
        try (var curator = connectCurator()) {
            assertNotNull(curator.checkExists().forPath("/first-boot"),
                    "control-service should create the /first-boot znode");
        }
    }
}
