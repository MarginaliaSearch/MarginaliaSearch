package nu.marginalia.assistant;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the assistant-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class AssistantServiceDockerTest extends AbstractDockerServiceTest {

    public AssistantServiceDockerTest() {
        super("assistant-service");
    }
}
