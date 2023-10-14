package nu.marginalia.actor;

import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessageRow;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorResumeBehavior;
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
public class ActorStateMachineResumeTest {
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

    public static class ResumeTrialsPrototypeActor extends AbstractActorPrototype {

        public ResumeTrialsPrototypeActor(ActorStateFactory stateFactory) {
            super(stateFactory);
        }

        public String describe() {
            return "Test graph";
        }
        @ActorState(name = "INITIAL", next = "RESUMABLE")
        public void initial() {}
        @ActorState(name = "RESUMABLE", next = "NON-RESUMABLE", resume = ActorResumeBehavior.RETRY)
        public void resumable() {}
        @ActorState(name = "NON-RESUMABLE", next = "OK", resume = ActorResumeBehavior.ERROR)
        public void nonResumable() {}

        @ActorState(name = "OK", next = "END")
        public void ok() {}

    }

    @Test
    public void smResumeResumableFromNew() throws Exception {
        var stateFactory = new ActorStateFactory(new GsonBuilder().create());

        sendMessage(inboxId, 0, "RESUMABLE");

        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new ResumeTrialsPrototypeActor(stateFactory));

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId, 0)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("RESUMABLE", "NON-RESUMABLE", "OK", "END"), states);
    }

    private long sendMessage(String inboxId, int node, String function) throws Exception {
        return persistence.sendNewMessage(inboxId+":"+node,  null, -1L, function, "", null);
    }

    @Test
    public void smResumeFromAck() throws Exception {
        var stateFactory = new ActorStateFactory(new GsonBuilder().create());

        long id = sendMessage(inboxId, 0, "RESUMABLE");
        persistence.updateMessageState(id, MqMessageState.ACK);

        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new ResumeTrialsPrototypeActor(stateFactory));

        sm.join(4, TimeUnit.SECONDS);
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId, 0)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("RESUMABLE", "RESUMABLE", "NON-RESUMABLE", "OK", "END"), states);
    }


    @Test
    public void smResumeNonResumableFromNew() throws Exception {
        var stateFactory = new ActorStateFactory(new GsonBuilder().create());

        sendMessage(inboxId, 0, "NON-RESUMABLE");

        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new ResumeTrialsPrototypeActor(stateFactory));

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId, 0)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("NON-RESUMABLE", "OK", "END"), states);
    }

    @Test
    public void smResumeNonResumableFromAck() throws Exception {
        var stateFactory = new ActorStateFactory(new GsonBuilder().create());


        long id = sendMessage(inboxId, 0, "NON-RESUMABLE");
        persistence.updateMessageState(id, MqMessageState.ACK);

        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new ResumeTrialsPrototypeActor(stateFactory));

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId, 0)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("NON-RESUMABLE", "ERROR"), states);
    }

    @Test
    public void smResumeEmptyQueue() throws Exception {
        var stateFactory = new ActorStateFactory(new GsonBuilder().create());

        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new ResumeTrialsPrototypeActor(stateFactory));

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId, 0)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of(), states);
    }
}
