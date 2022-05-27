package nu.marginalia.wmsa.edge;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static nu.marginalia.wmsa.configuration.ServiceDescriptor.ENCYCLOPEDIA;

@Tag("e2e")
@Testcontainers
public class EncyclopediaE2ETest extends E2ETestBase {
    @Container
    public GenericContainer<?> mariaDB = getMariaDBContainer();

    @Container
    public GenericContainer<?> encyclopediaContainer =  forService(ENCYCLOPEDIA, mariaDB);


    @Test
    public void run() {
    }
}
