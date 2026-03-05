package nu.marginalia.query;

import nu.marginalia.test.AbstractDockerServiceTest;

/** Starts the query-service Docker image with its dependencies to verify
 *  the container boots successfully end-to-end.
 */
public class QueryServiceDockerTest extends AbstractDockerServiceTest {

    public QueryServiceDockerTest() {
        super("query-service");
    }
}
