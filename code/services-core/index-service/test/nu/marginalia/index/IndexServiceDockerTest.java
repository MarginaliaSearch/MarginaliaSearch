package nu.marginalia.index;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the index-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class IndexServiceDockerTest extends AbstractDockerServiceTest {

    public IndexServiceDockerTest() {
        super("index-service");
    }
}
