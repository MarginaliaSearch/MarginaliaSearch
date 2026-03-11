package nu.marginalia.api;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the api-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class ApiServiceDockerTest extends AbstractDockerServiceTest {

    public ApiServiceDockerTest() {
        super("api-service");
    }
}
