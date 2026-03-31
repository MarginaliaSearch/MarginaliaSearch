package nu.marginalia.functions.domains;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@Execution(SAME_THREAD)
@Tag("slow")
public class DomainInformationServiceTest {

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    static DbDomainQueries dbDomainQueries;
    static GeoIpDictionary geoIpDictionary;
    static AggregateLinkGraphClient linkGraphClient;

    static DomainInformationService service;

    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);

        dbDomainQueries = mock(DbDomainQueries.class);
        geoIpDictionary = mock(GeoIpDictionary.class);
        linkGraphClient = mock(AggregateLinkGraphClient.class);

        when(geoIpDictionary.getAsnInfo(anyString())).thenReturn(Optional.empty());
        when(geoIpDictionary.getCountry(anyString())).thenReturn("");
        when(linkGraphClient.countLinksToDomain(anyInt())).thenReturn(5);
        when(linkGraphClient.countLinksFromDomain(anyInt())).thenReturn(3);

        service = new DomainInformationService(dbDomainQueries, geoIpDictionary, linkGraphClient, dataSource);
    }

    @AfterAll
    public static void tearDown() {
        dataSource.close();
        mariaDBContainer.close();
    }

    @AfterEach
    public void cleanDb() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM DOMAIN_SECURITY_INFORMATION");
            stmt.executeUpdate("DELETE FROM DOMAIN_AVAILABILITY_INFORMATION");
            stmt.executeUpdate("DELETE FROM DOMAIN_METADATA");
            stmt.executeUpdate("DELETE FROM CRAWL_QUEUE");
            stmt.executeUpdate("DELETE FROM EC_DOMAIN");
        }
    }

    private int insertDomain(String domainName, String ip, String state, double rank) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     "INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, IP, STATE, RANK, NODE_AFFINITY) VALUES (?, ?, ?, ?, ?, 1)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, domainName);
            stmt.setString(2, domainName);
            stmt.setString(3, ip);
            stmt.setString(4, state);
            stmt.setDouble(5, rank);
            stmt.executeUpdate();

            var keys = stmt.getGeneratedKeys();
            assertTrue(keys.next());
            return keys.getInt(1);
        }
    }

    @Test
    void domainInfoReturnsEmptyForUnknownDomain() {
        when(dbDomainQueries.getDomain(999)).thenReturn(Optional.empty());

        var result = service.domainInfo(999);
        assertTrue(result.isEmpty());
    }

    @Test
    void domainInfoReturnsBasicDomainFields() throws SQLException {
        int domainId = insertDomain("example.com", "1.2.3.4", "ACTIVE", 0.25);

        when(dbDomainQueries.getDomain(domainId)).thenReturn(Optional.of(new EdgeDomain("example.com")));

        var result = service.domainInfo(domainId);
        assertTrue(result.isPresent());

        var info = result.get();
        assertEquals("example.com", info.getDomain());
        assertEquals("1.2.3.4", info.getIp());
        assertEquals("ACTIVE", info.getState());
        assertEquals(1, info.getNodeAffinity());
        assertEquals(75, info.getRanking());
        assertEquals(5, info.getIncomingLinks());
        assertEquals(3, info.getOutboundLinks());
    }

    @Test
    void domainInfoIncludesMetadata() throws SQLException {
        int domainId = insertDomain("example.com", "1.2.3.4", "ACTIVE", 0.5);

        when(dbDomainQueries.getDomain(domainId)).thenReturn(Optional.of(new EdgeDomain("example.com")));

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     "INSERT INTO DOMAIN_METADATA (ID, KNOWN_URLS, VISITED_URLS, GOOD_URLS) VALUES (?, ?, ?, ?)")) {
            stmt.setInt(1, domainId);
            stmt.setInt(2, 100);
            stmt.setInt(3, 80);
            stmt.setInt(4, 60);
            stmt.executeUpdate();
        }

        var result = service.domainInfo(domainId);
        assertTrue(result.isPresent());

        var info = result.get();
        assertEquals(100, info.getPagesKnown());
        assertEquals(80, info.getPagesFetched());
        assertEquals(60, info.getPagesIndexed());
        assertFalse(info.getSuggestForCrawling());
    }

    @Test
    void domainInfoDetectsCrawlQueue() throws SQLException {
        int domainId = insertDomain("example.com", "1.2.3.4", "ACTIVE", 0.5);

        when(dbDomainQueries.getDomain(domainId)).thenReturn(Optional.of(new EdgeDomain("example.com")));

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("INSERT INTO CRAWL_QUEUE (DOMAIN_NAME, SOURCE) VALUES (?, ?)")) {
            stmt.setString(1, "example.com");
            stmt.setString(2, "test");
            stmt.executeUpdate();
        }

        var result = service.domainInfo(domainId);
        assertTrue(result.isPresent());
        assertTrue(result.get().getInCrawlQueue());
        assertFalse(result.get().getSuggestForCrawling());
    }

    @Test
    void domainInfoSuggestsCrawlingWhenNoPagesAndNotInQueue() throws SQLException {
        int domainId = insertDomain("example.com", "1.2.3.4", "ACTIVE", 0.5);

        when(dbDomainQueries.getDomain(domainId)).thenReturn(Optional.of(new EdgeDomain("example.com")));

        var result = service.domainInfo(domainId);
        assertTrue(result.isPresent());
        assertTrue(result.get().getSuggestForCrawling());
    }

    @Test
    void domainInfoIncludesPingData() throws SQLException {
        int domainId = insertDomain("example.com", "1.2.3.4", "ACTIVE", 0.5);

        when(dbDomainQueries.getDomain(domainId)).thenReturn(Optional.of(new EdgeDomain("example.com")));

        Timestamp now = Timestamp.from(Instant.now());
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO DOMAIN_AVAILABILITY_INFORMATION
                     (DOMAIN_ID, NODE_ID, SERVER_AVAILABLE, HTTP_SCHEMA, HTTP_RESPONSE_TIME_MS,
                      ERROR_CLASSIFICATION, BACKOFF_CONSECUTIVE_FAILURES,
                      TS_LAST_PING, TS_LAST_AVAILABLE, NEXT_SCHEDULED_UPDATE)
                     VALUES (?, 1, TRUE, 'HTTPS', 42, 'NONE', 2, ?, ?, ?)
                     """)) {
            stmt.setInt(1, domainId);
            stmt.setTimestamp(2, now);
            stmt.setTimestamp(3, now);
            stmt.setTimestamp(4, now);
            stmt.executeUpdate();
        }

        var result = service.domainInfo(domainId);
        assertTrue(result.isPresent());
        assertTrue(result.get().hasPingData());

        var ping = result.get().getPingData();
        assertTrue(ping.getServerAvailable());
        assertEquals(42, ping.getResponseTimeMs());
        assertEquals(2, ping.getConsecutiveFailures());
        assertEquals("HTTPS", ping.getHttpSchema());
        assertEquals("NONE", ping.getErrorClassification());
    }

    @Test
    void domainInfoIncludesSecurityData() throws SQLException {
        int domainId = insertDomain("example.com", "1.2.3.4", "ACTIVE", 0.5);

        when(dbDomainQueries.getDomain(domainId)).thenReturn(Optional.of(new EdgeDomain("example.com")));

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO DOMAIN_SECURITY_INFORMATION
                     (DOMAIN_ID, NODE_ID, HTTP_VERSION, HTTP_COMPRESSION,
                      HEADER_SERVER, SSL_PROTOCOL, SSL_CIPHER_SUITE,
                      SSL_CERT_WILDCARD, SSL_CHAIN_VALID, SSL_DATE_VALID, SSL_HOST_VALID)
                     VALUES (?, 1, 'HTTP/2', TRUE, 'nginx', 'TLSv1.3', 'TLS_AES_256_GCM_SHA384',
                             FALSE, TRUE, TRUE, TRUE)
                     """)) {
            stmt.setInt(1, domainId);
            stmt.executeUpdate();
        }

        var result = service.domainInfo(domainId);
        assertTrue(result.isPresent());
        assertTrue(result.get().hasSecurityData());

        var sec = result.get().getSecurityData();
        assertEquals("HTTP/2", sec.getHttpVersion());
        assertTrue(sec.getHttpCompression());
        assertEquals("nginx", sec.getHeaderServer());
        assertEquals("TLSv1.3", sec.getSslProtocol());
        assertEquals("TLS_AES_256_GCM_SHA384", sec.getSslCipherSuite());
        assertFalse(sec.getSslCertWildcard());
        assertTrue(sec.getSslChainValid());
        assertTrue(sec.getSslChainDateValid());
        assertTrue(sec.getSslChainHostValid());
    }

    @Test
    void domainInfoWithNoOptionalTablesPopulated() throws SQLException {
        int domainId = insertDomain("example.com", "1.2.3.4", "ACTIVE", 0.5);

        when(dbDomainQueries.getDomain(domainId)).thenReturn(Optional.of(new EdgeDomain("example.com")));

        var result = service.domainInfo(domainId);
        assertTrue(result.isPresent());

        var info = result.get();
        assertEquals(0, info.getPagesKnown());
        assertEquals(0, info.getPagesFetched());
        assertEquals(0, info.getPagesIndexed());
        assertFalse(info.hasPingData());
        assertFalse(info.hasSecurityData());
        assertFalse(info.getInCrawlQueue());
        assertTrue(info.getSuggestForCrawling());
    }
}
