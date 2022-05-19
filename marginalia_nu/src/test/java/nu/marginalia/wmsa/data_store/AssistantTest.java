package nu.marginalia.wmsa.data_store;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.client.exception.RemoteException;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryService;
import nu.marginalia.wmsa.edge.assistant.dict.SpellChecker;
import nu.marginalia.wmsa.edge.assistant.eval.MathParser;
import nu.marginalia.wmsa.edge.assistant.eval.Units;
import nu.marginalia.wmsa.edge.assistant.EdgeAssistantService;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.search.UnitConversion;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import spark.Spark;

import static nu.marginalia.util.TestUtil.getConnection;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class AssistantTest {
    static EdgeAssistantService service;
    static AssistantClient client;

    private static HikariDataSource dataSource;

    static int testPort = TestUtil.getPort();

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
                null, null,
                new ScreenshotService(null), null);

        Spark.awaitInitialization();
    }

    @BeforeEach
    public void clearDb() {
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
        Spark.awaitStop();
    }

    @Test
    public void testEncyclopedia() {
        var result = client.encyclopediaLookup(Context.internal(), "plato").blockingFirst();
        System.out.println(result);
        assertTrue(result.entries.size() >= 1);
    }
    @Test
    public void testSpellCheck() {
        var result = client.spellCheck(Context.internal(), "plato").blockingFirst();
        System.out.println(result);
    }
    @Test
    public void testDictionary() {
        var result = client.dictionaryLookup(Context.internal(), "adiabatic").blockingFirst();
        System.out.println(result);
        assertTrue(result.entries.size() > 1);
    }

    @Test
    public void testDictionaryNoQuery() {
        var result = client.dictionaryLookup(Context.internal(), "vlofgren").blockingFirst();
        System.out.println(result);
        assertTrue(result.entries.isEmpty());
    }

    @Test
    public void testEncyclopediaNoQuery() {
        var result = client.dictionaryLookup(Context.internal(), "vlofgren").blockingFirst();
        System.out.println(result);
        assertTrue(result.entries.isEmpty());
    }

    @Test
    public void testConvertUnitsWithParser() {
        var conversion = new UnitConversion(client);
        assertEquals("0.3 m", conversion.tryConversion(Context.internal(), "30 cm in m").get());
        assertEquals("500 m", conversion.tryConversion(Context.internal(), "0.5 km in m").get());
        assertEquals("500 m", conversion.tryConversion(Context.internal(), "0.1+0.4 km in m").get());
        assertTrue(conversion.tryConversion(Context.internal(), "0.5 km in F").isEmpty());
        assertTrue(conversion.tryConversion(Context.internal(), "plato").isEmpty());
    }

    @Test
    public void testConvertUnits() {
        assertEquals("5 m", client.unitConversion(Context.internal(), "500", "cm", "meters").blockingFirst());
    }

    @Test
    public void testEvalmath() {
        assertEquals("300", client.evalMath(Context.internal(), "3*10^2").blockingFirst());
    }

    @Test
    public void testEvalWithParser() {
        var conversion = new UnitConversion(client);
        assertEquals("305", conversion.tryEval(Context.internal(), "300+5").get());
        assertEquals("1.772", conversion.tryEval(Context.internal(), "sqrt(pi)").get());

    }


    @Test
    public void testConvertUnitsWeirdError() {
        try {
            client.unitConversion(Context.internal(), "500", "kg", "meters").blockingFirst();
            fail("Wanted exception");
        }
        catch (RemoteException ex) {

        }
    }
}