package nu.marginalia.mq.outbox;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqInbox;
import nu.marginalia.mq.inbox.MqSubscription;
import nu.marginalia.mq.persistence.MqPersistence;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
@Testcontainers
public class MqOutboxTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("sql/current/11-message-queue.sql")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
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
    }

    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
    }

    @Test
    public void testOpenClose() throws InterruptedException {
        var outbox = new MqOutbox(new MqPersistence(dataSource), inboxId, inboxId+"/reply", UUID.randomUUID());
        outbox.stop();
    }

    @Test
    public void testSend() throws Exception {
        var outbox = new MqOutbox(new MqPersistence(dataSource), inboxId,inboxId+"/reply", UUID.randomUUID());
        Executors.newSingleThreadExecutor().submit(() -> outbox.send("test", "Hello World"));

        TimeUnit.MILLISECONDS.sleep(100);

        var messages = MqTestUtil.getMessages(dataSource, inboxId);
        assertEquals(1, messages.size());
        System.out.println(messages.get(0));

        outbox.stop();
    }

    @Test
    public void testSendAndRespond() throws Exception {
        var outbox = new MqOutbox(new MqPersistence(dataSource), inboxId,inboxId+"/reply", UUID.randomUUID());

        var inbox = new MqInbox(new MqPersistence(dataSource), inboxId, UUID.randomUUID());
        inbox.subscribe(justRespond("Alright then"));
        inbox.start();

        var rsp = outbox.send("test", "Hello World");

        assertEquals(MqMessageState.OK, rsp.state());
        assertEquals("Alright then", rsp.payload());

        var messages = MqTestUtil.getMessages(dataSource, inboxId);
        assertEquals(1, messages.size());
        assertEquals(MqMessageState.OK, messages.get(0).state());

        outbox.stop();
        inbox.stop();
    }

    @Test
    public void testSendMultiple() throws Exception {
        var outbox = new MqOutbox(new MqPersistence(dataSource), inboxId,inboxId+"/reply", UUID.randomUUID());

        var inbox = new MqInbox(new MqPersistence(dataSource), inboxId, UUID.randomUUID());
        inbox.subscribe(echo());
        inbox.start();

        var rsp1 = outbox.send("test", "one");
        var rsp2 = outbox.send("test", "two");
        var rsp3 = outbox.send("test", "three");
        var rsp4 = outbox.send("test", "four");

        Thread.sleep(500);

        assertEquals(MqMessageState.OK, rsp1.state());
        assertEquals("one", rsp1.payload());
        assertEquals(MqMessageState.OK, rsp2.state());
        assertEquals("two", rsp2.payload());
        assertEquals(MqMessageState.OK, rsp3.state());
        assertEquals("three", rsp3.payload());
        assertEquals(MqMessageState.OK, rsp4.state());
        assertEquals("four", rsp4.payload());

        var messages = MqTestUtil.getMessages(dataSource, inboxId);
        assertEquals(4, messages.size());
        for (var message : messages) {
            assertEquals(MqMessageState.OK, message.state());
        }

        outbox.stop();
        inbox.stop();
    }

    @Test
    public void testSendAndRespondWithErrorHandler() throws Exception {
        var outbox = new MqOutbox(new MqPersistence(dataSource), inboxId,inboxId+"/reply", UUID.randomUUID());
        var inbox = new MqInbox(new MqPersistence(dataSource), inboxId, UUID.randomUUID());

        inbox.start();

        var rsp = outbox.send("test", "Hello World");

        assertEquals(MqMessageState.ERR, rsp.state());

        var messages = MqTestUtil.getMessages(dataSource, inboxId);
        assertEquals(1, messages.size());
        assertEquals(MqMessageState.ERR, messages.get(0).state());

        outbox.stop();
        inbox.stop();
    }

    public MqSubscription justRespond(String response) {
        return new MqSubscription() {
            @Override
            public boolean filter(MqMessage rawMessage) {
                return true;
            }

            @Override
            public MqInboxResponse onRequest(MqMessage msg) {
                return MqInboxResponse.ok(response);
            }

            @Override
            public void onNotification(MqMessage msg) { }
        };
    }

    public MqSubscription echo() {
        return new MqSubscription() {
            @Override
            public boolean filter(MqMessage rawMessage) {
                return true;
            }

            @Override
            public MqInboxResponse onRequest(MqMessage msg) {
                return MqInboxResponse.ok(msg.payload());
            }

            @Override
            public void onNotification(MqMessage msg) {}
        };
    }

}