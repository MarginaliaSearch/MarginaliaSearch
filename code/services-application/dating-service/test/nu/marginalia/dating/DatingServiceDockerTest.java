package nu.marginalia.dating;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the dating-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class DatingServiceDockerTest extends AbstractDockerServiceTest {

    public DatingServiceDockerTest() {
        super("dating-service");
    }
}
