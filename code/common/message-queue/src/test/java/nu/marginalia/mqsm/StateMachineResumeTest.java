package nu.marginalia.mqsm;

import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqMessageRow;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.StateGraph;
import nu.marginalia.mqsm.state.ResumeBehavior;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
@Testcontainers
public class StateMachineResumeTest {
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

    public static class ResumeTrialsGraph extends StateGraph {

        public ResumeTrialsGraph(StateFactory stateFactory) {
            super(stateFactory);
        }

        @GraphState(name = "INITIAL", next = "RESUMABLE")
        public void initial() {}
        @GraphState(name = "RESUMABLE", next = "NON-RESUMABLE", resume = ResumeBehavior.RETRY)
        public void resumable() {}
        @GraphState(name = "NON-RESUMABLE", next = "OK", resume = ResumeBehavior.ERROR)
        public void nonResumable() {}

        @GraphState(name = "OK", next = "END")
        public void ok() {}

    }

    @Test
    public void smResumeResumableFromNew() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        sm.registerStates(new ResumeTrialsGraph(stateFactory).asStateList());

        persistence.sendNewMessage(inboxId,  null,"RESUMABLE", "", null);

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("RESUMABLE", "NON-RESUMABLE", "OK", "END"), states);
    }

    @Test
    public void smResumeFromAck() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        sm.registerStates(new ResumeTrialsGraph(stateFactory));

        long id = persistence.sendNewMessage(inboxId,  null,"RESUMABLE", "", null);
        persistence.updateMessageState(id, MqMessageState.ACK);

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("RESUMABLE", "NON-RESUMABLE", "OK", "END"), states);
    }


    @Test
    public void smResumeNonResumableFromNew() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        sm.registerStates(new ResumeTrialsGraph(stateFactory));

        persistence.sendNewMessage(inboxId,  null,"NON-RESUMABLE", "", null);

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("NON-RESUMABLE", "OK", "END"), states);
    }

    @Test
    public void smResumeNonResumableFromAck() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        sm.registerStates(new ResumeTrialsGraph(stateFactory));

        long id = persistence.sendNewMessage(inboxId,  null,"NON-RESUMABLE", "", null);
        persistence.updateMessageState(id, MqMessageState.ACK);

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("NON-RESUMABLE", "ERROR"), states);
    }

    @Test
    public void smResumeEmptyQueue() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        sm.registerStates(new ResumeTrialsGraph(stateFactory));

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("INITIAL", "RESUMABLE", "NON-RESUMABLE", "OK", "END"), states);
    }
}
