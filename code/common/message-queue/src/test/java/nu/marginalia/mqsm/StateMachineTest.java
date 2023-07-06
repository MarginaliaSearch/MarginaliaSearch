package nu.marginalia.mqsm;

import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
@Testcontainers
public class StateMachineTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("sql/current/11-message-queue.sql")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static MqPersistence persistence;
    private String inboxId;

    @BeforeEach
    public void setUp() {
        inboxId = UUID.randomUUID().toString();
    }
    @BeforeAll
    public static void setUpAll() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        persistence = new MqPersistence(dataSource);
    }

    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
    }

    public static class TestGraph extends AbstractStateGraph {
        public TestGraph(StateFactory stateFactory) {
            super(stateFactory);
        }

        @GraphState(name = "INITIAL", next = "GREET")
        public String initial() {
            return "World";
        }

        @GraphState(name = "GREET")
        public void greet(String message) {
            System.out.println("Hello, " + message + "!");

            transition("COUNT-DOWN", 5);
        }

        @GraphState(name = "COUNT-DOWN", next = "END")
        public void countDown(Integer from) {
            if (from > 0) {
                System.out.println(from);
                transition("COUNT-DOWN", from - 1);
            }
        }
    }

    @Test
    public void testAnnotatedStateGraph() throws Exception {
        var stateFactory = new StateFactory(new GsonBuilder().create());
        var graph = new TestGraph(stateFactory);


        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID(), graph);
        sm.registerStates(graph);

        sm.init();

        sm.join();
        sm.stop();

        MqTestUtil.getMessages(dataSource, inboxId).forEach(System.out::println);

    }

    @Test
    public void testStartStopStartStop() throws Exception {
        var stateFactory = new StateFactory(new GsonBuilder().create());
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID(), new TestGraph(stateFactory));

        sm.init();

        Thread.sleep(150);
        sm.stop();

        System.out.println("-------------------- ");

        var sm2 = new StateMachine(persistence, inboxId, UUID.randomUUID(), new TestGraph(stateFactory));
        sm2.resume();
        sm2.join();
        sm2.stop();

        MqTestUtil.getMessages(dataSource, inboxId).forEach(System.out::println);
    }

}
