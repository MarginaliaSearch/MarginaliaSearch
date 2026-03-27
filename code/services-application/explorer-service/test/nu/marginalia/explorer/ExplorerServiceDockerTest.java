package nu.marginalia.explorer;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the explorer-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class ExplorerServiceDockerTest extends AbstractDockerServiceTest {

    public ExplorerServiceDockerTest() {
        super("explorer-service");
    }
}
