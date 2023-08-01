package nu.marginalia.mqsm;

import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessageRow;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Tag("slow")
@Testcontainers
@Execution(SAME_THREAD)
public class StateMachineErrorTest {
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

    public static class ErrorHurdles extends AbstractStateGraph {

        public ErrorHurdles(StateFactory stateFactory) {
            super(stateFactory);
        }

        @GraphState(name = "INITIAL", next = "FAILING")
        public void initial() {

        }
        @GraphState(name = "FAILING", next = "OK", resume = ResumeBehavior.RETRY)
        public void resumable() {
            throw new RuntimeException("Boom!");
        }
        @GraphState(name = "OK", next = "END")
        public void ok() {

        }

    }

    @Test
    public void smResumeResumableFromNew() throws Exception {
        var stateFactory = new StateFactory(new GsonBuilder().create());
        var sm = new StateMachine(messageQueueFactory, inboxId, UUID.randomUUID(), new ErrorHurdles(stateFactory));

        sm.init();

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("INITIAL", "FAILING", "ERROR"), states);
    }

}
