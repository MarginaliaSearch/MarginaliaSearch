package nu.marginalia.search;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the search-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class SearchServiceDockerTest extends AbstractDockerServiceTest {

    public SearchServiceDockerTest() {
        super("search-service");
    }
}
