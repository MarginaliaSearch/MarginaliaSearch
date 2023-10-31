package nu.marginalia.actor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.persistence.MqPersistence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Tag("slow")
@Testcontainers
@Execution(SAME_THREAD)
public class ActorRecordMachineTest {
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

    public static class TestPrototypeActor extends RecordActorPrototype {
        public TestPrototypeActor(Gson gson)
        {
            super(gson);
        }

        public record Initial() implements ActorStep {}
        public record Greet(String message) implements ActorStep {}
        public record CountDown(int from) implements ActorStep {}

        @Override
        public ActorStep transition(ActorStep self) {
            return switch (self) {
                case Initial i -> new Greet("World");
                case Greet(String name) -> {
                    System.out.println("Hello " + name + "!");
                    yield new CountDown(5);
                }
                case CountDown (int from) -> {
                    if (from > 0) {
                        System.out.println(from);
                        yield new CountDown(from - 1);
                    }
                    yield new End();
                }
                default -> new Error();
            };
        }

        public String describe() {
            return "Test graph";
        }

    }

    @Test
    public void testAnnotatedStateGraph() throws Exception {
        var graph = new TestPrototypeActor(new GsonBuilder().create());
        graph.asStateList().forEach(i -> {
            System.out.println(i.name());
        });


        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), graph);
        sm.registerStates(graph);

        sm.init();

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        MqTestUtil.getMessages(dataSource, inboxId, 0).forEach(System.out::println);

    }

    @Test
    public void testStartStopStartStop() throws Exception {
        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new TestPrototypeActor(new GsonBuilder().create()));

        sm.init();

        Thread.sleep(150);
        sm.stop();

        System.out.println("-------------------- ");

        var sm2 = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new TestPrototypeActor(new GsonBuilder().create()));
        sm2.join(2, TimeUnit.SECONDS);
        sm2.stop();

        MqTestUtil.getMessages(dataSource, inboxId, 0).forEach(System.out::println);
    }

    @Test
    public void testFalseTransition() throws Exception {
        // Prep the queue with a message to set the state to initial,
        // and an additional message to trigger the false transition back to initial

        persistence.sendNewMessage(inboxId,  null, null, "INITIAL", "", null);
        persistence.sendNewMessage(inboxId,  null, null, "INITIAL", "", null);

        var sm = new ActorStateMachine(messageQueueFactory, inboxId, 0, UUID.randomUUID(), new TestPrototypeActor(new GsonBuilder().create()));

        Thread.sleep(50);

        sm.join(2, TimeUnit.SECONDS);
        sm.stop();

        MqTestUtil.getMessages(dataSource, inboxId, 0).forEach(System.out::println);
    }

}
