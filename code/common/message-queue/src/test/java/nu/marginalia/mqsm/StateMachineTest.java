package nu.marginalia.mqsm;

import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqMessageRow;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.persistence.MqPersistence;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
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

    @Test
    public void testStartStopStartStop() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        var initial = stateFactory.create("INITIAL", () -> stateFactory.transition("GREET", "World"));

        var greet = stateFactory.create("GREET", String.class, (String message) -> {
            System.out.println("Hello, " + message + "!");
            return stateFactory.transition("COUNT-TO-FIVE", 0);
        });

        var ctf = stateFactory.create("COUNT-TO-FIVE", Integer.class, (Integer count) -> {
            System.out.println(count);
            if (count < 5) {
                return stateFactory.transition("COUNT-TO-FIVE", count + 1);
            } else {
                return stateFactory.transition("END");
            }
        });

        sm.registerStates(initial, greet, ctf);

        sm.init();

        Thread.sleep(300);
        sm.stop();

        var sm2 = new StateMachine(persistence, inboxId, UUID.randomUUID());
        sm2.registerStates(initial, greet, ctf);
        sm2.resume();
        sm2.join();
        sm2.stop();

        MqTestUtil.getMessages(dataSource, inboxId).forEach(System.out::println);
    }

    @Test
    public void smResumeFromNew() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        var initial = stateFactory.create("INITIAL", () -> stateFactory.transition("A"));
        var stateA = stateFactory.create("A", () -> stateFactory.transition("B"));
        var stateB = stateFactory.create("B", () -> stateFactory.transition("C"));
        var stateC = stateFactory.create("C", () -> stateFactory.transition("END"));

        sm.registerStates(initial, stateA, stateB, stateC);
        persistence.sendNewMessage(inboxId,  null,"B", "", null);

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("B", "C", "END"), states);
    }

    @Test
    public void smResumeFromAck() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        var initial = stateFactory.create("INITIAL", () -> stateFactory.transition("A"));
        var stateA = stateFactory.create("A", () -> stateFactory.transition("B"));
        var stateB = stateFactory.create("B", () -> stateFactory.transition("C"));
        var stateC = stateFactory.create("C", () -> stateFactory.transition("END"));

        sm.registerStates(initial, stateA, stateB, stateC);

        long id = persistence.sendNewMessage(inboxId,  null,"B", "", null);
        persistence.updateMessageState(id, MqMessageState.ACK);

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("B", "C", "END"), states);
    }


    @Test
    public void smResumeEmptyQueue() throws Exception {
        var sm = new StateMachine(persistence, inboxId, UUID.randomUUID());
        var stateFactory = new StateFactory(new GsonBuilder().create());

        var initial = stateFactory.create("INITIAL", () -> stateFactory.transition("A"));
        var stateA = stateFactory.create("A", () -> stateFactory.transition("B"));
        var stateB = stateFactory.create("B", () -> stateFactory.transition("C"));
        var stateC = stateFactory.create("C", () -> stateFactory.transition("END"));

        sm.registerStates(initial, stateA, stateB, stateC);

        sm.resume();

        sm.join();
        sm.stop();

        List<String> states = MqTestUtil.getMessages(dataSource, inboxId)
                .stream()
                .peek(System.out::println)
                .map(MqMessageRow::function)
                .toList();

        assertEquals(List.of("INITIAL", "A", "B", "C", "END"), states);
    }
}
