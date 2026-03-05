package nu.marginalia.status;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the status-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class StatusServiceDockerTest extends AbstractDockerServiceTest {

    public StatusServiceDockerTest() {
        super("status-service");
    }
}
