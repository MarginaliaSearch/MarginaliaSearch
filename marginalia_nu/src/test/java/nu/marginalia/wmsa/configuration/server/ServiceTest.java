package nu.marginalia.wmsa.configuration.server;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.client.exception.RemoteException;
import nu.marginalia.wmsa.edge.assistant.EdgeAssistantService;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryService;
import nu.marginalia.wmsa.edge.assistant.dict.SpellChecker;
import nu.marginalia.wmsa.edge.assistant.eval.MathParser;
import nu.marginalia.wmsa.edge.assistant.eval.Units;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Spark;

import static nu.marginalia.util.TestUtil.getConnection;

class ServiceTest {
    static EdgeAssistantService service;
    static AssistantClient client;

    private static HikariDataSource dataSource;

    static final int testPort = TestUtil.getPort();

    @SneakyThrows
    public static HikariDataSource provideConnection() {
        return getConnection();
    }


    @BeforeAll
    public static void setUpClass() {
        Spark.port(testPort);
        System.setProperty("service-name", "test");

        dataSource = provideConnection();
        dataSource.setKeepaliveTime(100);
        dataSource.setIdleTimeout(100);

        client = new AssistantClient();
        client.setServiceRoute("127.0.0.1", testPort);

        service = new EdgeAssistantService("127.0.0.1",
                testPort,
                new Initialization(), null,
                new DictionaryService(dataSource, new SpellChecker()),
                new MathParser(),
                new Units(new MathParser()),
                null,
                null,
                new ScreenshotService(null), null);

        Spark.awaitInitialization();
    }

    @Test
    public void testDenyXPublic() {
        try {
            client.ping(Context.internal().treatAsPublic()).blockingSubscribe();
            Assertions.fail("Expected exception");
        }
        catch (RemoteException ex) {
            //
        }
    }
    @Test
    public void testAllowInternalNoXPublic() {
        client.ping(Context.internal()).blockingSubscribe();
    }

    @Test
    public void testAllowOnPublic() {
        Assertions.assertEquals("EdgeAssistantService", client.who(Context.internal()).blockingFirst());
        Assertions.assertEquals("EdgeAssistantService", client.who(Context.internal().treatAsPublic()).blockingFirst());
    }

}