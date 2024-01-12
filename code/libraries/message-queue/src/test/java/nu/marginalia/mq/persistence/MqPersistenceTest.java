package nu.marginalia.mq.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.MqTestUtil;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("slow")
@Testcontainers
public class MqPersistenceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static MqPersistence persistence;
    String recipientId;
    String senderId;

    @BeforeEach
    public void setUp() {
        senderId = UUID.randomUUID().toString();
        recipientId = UUID.randomUUID().toString();
    }

    @BeforeAll
    public static void setUpAll() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        persistence = new MqPersistence(dataSource);
        TestMigrationLoader.flywayMigration(dataSource);
    }

    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
    }

    public long sendMessage(String recipient, String sender, String function, String payload, Duration ttl) throws Exception {
        return persistence.sendNewMessage(recipient+":0", sender != null ? (sender+":0") : null, null, function, payload, ttl);
    }

    @Test
    public void testReaper() throws Exception {

        sendMessage(recipientId, senderId, "function", "payload", Duration.ofSeconds(2));

        persistence.reapDeadMessages();

        var messages = MqTestUtil.getMessages(dataSource, recipientId, 0);
        assertEquals(1, messages.size());
        assertEquals(MqMessageState.NEW, messages.get(0).state());
        System.out.println(messages);

        TimeUnit.SECONDS.sleep(5);

        persistence.reapDeadMessages();

        messages = MqTestUtil.getMessages(dataSource, recipientId, 0);
        assertEquals(1, messages.size());
        assertEquals(MqMessageState.DEAD, messages.get(0).state());
    }

    @Test
    public void sendWithReplyAddress() throws Exception {

        long id = sendMessage(recipientId, senderId, "function", "payload", Duration.ofSeconds(30));

        var messages = MqTestUtil.getMessages(dataSource, recipientId, 0);
        assertEquals(1, messages.size());

        var message = messages.get(0);

        assertEquals(id, message.id());
        assertEquals("function", message.function());
        assertEquals("payload", message.payload());
        assertEquals(MqMessageState.NEW, message.state());

        System.out.println(message);
    }

    @Test
    public void sendNoReplyAddress() throws Exception {

        long id = sendMessage(recipientId, null, "function", "payload", Duration.ofSeconds(30));

        var messages = MqTestUtil.getMessages(dataSource, recipientId, 0);
        assertEquals(1, messages.size());

        var message = messages.get(0);

        assertEquals(id, message.id());
        assertNull(message.senderInbox());
        assertEquals("function", message.function());
        assertEquals("payload", message.payload());
        assertEquals(MqMessageState.NEW, message.state());

        System.out.println(message);
    }

    @Test
    public void updateState() throws Exception {


        long id = sendMessage(recipientId, senderId, "function", "payload", Duration.ofSeconds(30));

        persistence.updateMessageState(id, MqMessageState.OK);
        System.out.println(id);

        var messages = MqTestUtil.getMessages(dataSource, recipientId, 0);
        assertEquals(1, messages.size());

        var message = messages.get(0);

        assertEquals(id, message.id());
        assertEquals(MqMessageState.OK, message.state());

        System.out.println(message);
    }

    @Test
    public void testReply() throws Exception {
        long request = sendMessage(recipientId, senderId, "function", "payload", Duration.ofSeconds(30));
        long response = persistence.sendResponse(request,  MqMessageState.OK, "response");

        var sentMessages = MqTestUtil.getMessages(dataSource, recipientId, 0);
        System.out.println(sentMessages);
        assertEquals(1, sentMessages.size());

        var requestMessage = sentMessages.get(0);
        assertEquals(request, requestMessage.id());
        assertEquals(MqMessageState.OK, requestMessage.state());


        var replies = MqTestUtil.getMessages(dataSource, senderId, 0);
        System.out.println(replies);
        assertEquals(1, replies.size());

        var responseMessage = replies.get(0);
        assertEquals(response, responseMessage.id());
        assertEquals(request, responseMessage.relatedId());
        assertEquals(MqMessageState.NEW, responseMessage.state());
    }

    @Test
    public void testPollInbox() throws Exception {

        String instanceId = "BATMAN";
        long tick = 1234L;

        long id = sendMessage(recipientId, null, "function", "payload", Duration.ofSeconds(30));

        var messagesPollFirstTime = persistence.pollInbox(recipientId+":0", instanceId , tick, 10);

        /** CHECK POLL RESULT */
        assertEquals(1, messagesPollFirstTime.size());
        var firstPollMessage = messagesPollFirstTime.iterator().next();
        assertEquals(id, firstPollMessage.msgId());
        assertEquals("function", firstPollMessage.function());
        assertEquals("payload", firstPollMessage.payload());

        /** CHECK DB TABLE */
        var messages = MqTestUtil.getMessages(dataSource, recipientId, 0);
        assertEquals(1, messages.size());

        var message = messages.get(0);

        assertEquals(id, message.id());
        assertEquals("function", message.function());
        assertEquals("payload", message.payload());
        assertEquals(MqMessageState.ACK, message.state());
        assertEquals(instanceId, message.ownerInstance());
        assertEquals(tick, message.ownerTick());

        /** VERIFY SECOND POLL IS EMPTY */
        var messagePollSecondTime = persistence.pollInbox(recipientId+":0", instanceId , 1, 10);
        assertEquals(0, messagePollSecondTime.size());
    }
}
