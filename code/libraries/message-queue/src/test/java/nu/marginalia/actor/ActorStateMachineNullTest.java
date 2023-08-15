package nu.marginalia.actor;

import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.actor.state.ActorState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Tag("slow")
@Testcontainers
@Execution(SAME_THREAD)
public class ActorStateMachineNullTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("db/migration/V23_07_0_003__message_queue.sql")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static MqPersistence persistence;
    static MessageQueueFactory messageQueueFactory;
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
        messageQueueFactory = new MessageQueueFactory(persistence);
    }

    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
    }

    public static class TestPrototypeActor extends AbstractActorPrototype {
        public TestPrototypeActor(ActorStateFactory stateFactory) {
            super(stateFactory);
        }

        public String describe() {
            return "Test graph";
        }
        @ActorState(name = "INITIAL", next = "GREET")
        public void initial() {}

        @ActorState(name = "GREET", next = "END")
        public void greet(String message) {
            if (null == message) {
                System.out.println("Hello, null!");
                return;
            }
            Assertions.fail("Should not be called");
        }

    }

    @Test
    public void testStateGraphNullSerialization() throws Exception {
        var stateFactory = new ActorStateFactory(new GsonBuilder().create());
        var graph = new TestPrototypeActor(stateFactory);


        var sm = new ActorStateMachine(messageQueueFactory, inboxId, UUID.randomUUID(), graph);
        sm.registerStates(graph);

        sm.init();

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        MqTestUtil.getMessages(dataSource, inboxId).forEach(System.out::println);

    }

}
