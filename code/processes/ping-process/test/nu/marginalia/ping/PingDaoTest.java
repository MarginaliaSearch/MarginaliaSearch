package nu.marginalia.ping;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.ping.model.*;
import nu.marginalia.ping.model.comparison.DomainDnsEvent;
import nu.marginalia.ping.util.JsonObject;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static nu.marginalia.ping.model.AvailabilityOutageType.TIMEOUT;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class PingDaoTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");


    static HikariDataSource dataSource;

    @BeforeAll
    public static void setup() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('www.marginalia.nu', 'marginalia.nu', 1)");
        }

    }

    @BeforeEach
    public void beforeEach() {
        // Reset the database state before each test if needed
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE  DOMAIN_AVAILABILITY_INFORMATION");
            stmt.execute("TRUNCATE TABLE  DOMAIN_DNS_INFORMATION");
            stmt.execute("TRUNCATE TABLE  DOMAIN_SECURITY_INFORMATION");
            stmt.execute("TRUNCATE TABLE  DOMAIN_AVAILABILITY_EVENTS");
            stmt.execute("TRUNCATE TABLE  DOMAIN_SECURITY_EVENTS");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    public static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    public void readWriteDomainPingObject() throws SQLException {
        var svc = new PingDao(dataSource);

        var writeStatus = new DomainAvailabilityRecord(
                1,
                2,
                false,
                new byte[]{127, 0, 0, 1}, // Example IP address
                40,
                0x0F00BA32L,
                0x0F00BA34L,
                HttpSchema.HTTP,
                "etag123",
                "Wed, 21 Oct 2023 07:28:00 GMT",
                501,
                "http://example.com/redirect",
                Duration.ofMillis(150),
                ErrorClassification.DNS_ERROR,
                "DNS resolution failed",
                Instant.now(),
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS),
                Instant.now().minus(7200, java.time.temporal.ChronoUnit.SECONDS),
                Instant.now().plus(3600, java.time.temporal.ChronoUnit.SECONDS),
                2,
                Duration.ofSeconds(60)
        );


        svc.write(writeStatus);

        var readStatus = svc.getDomainPingStatus(1);
        assert readStatus != null;
        assertEquals(writeStatus.asn(), readStatus.asn());
        assertEquals(writeStatus.domainId(), readStatus.domainId());
        assertEquals(writeStatus.nodeId(), readStatus.nodeId());
        assertEquals(writeStatus.serverAvailable(), readStatus.serverAvailable());
        assertArrayEquals(writeStatus.serverIp(), readStatus.serverIp());
        assertEquals(writeStatus.dataHash(), readStatus.dataHash());
        assertEquals(writeStatus.securityConfigHash(), readStatus.securityConfigHash());
        assertEquals(writeStatus.httpSchema(), readStatus.httpSchema());
        assertEquals(writeStatus.httpEtag(), readStatus.httpEtag());
        assertEquals(writeStatus.httpLastModified(), readStatus.httpLastModified());
        assertEquals(writeStatus.httpStatus(), readStatus.httpStatus());
        assertEquals(writeStatus.httpLocation(), readStatus.httpLocation());
        assertEquals(writeStatus.httpResponseTime(), readStatus.httpResponseTime());
        assertEquals(writeStatus.errorClassification(), readStatus.errorClassification());
        assertEquals(writeStatus.errorMessage(), readStatus.errorMessage());
        assertEquals(writeStatus.backoffConsecutiveFailures(), readStatus.backoffConsecutiveFailures());
        assertEquals(writeStatus.backoffFetchInterval(), readStatus.backoffFetchInterval());


        // Check timestamps, as the SQL server has worse resolution than Java's Instant, we need to be lenient
        assertTrue(Duration.between(writeStatus.nextScheduledUpdate(), readStatus.nextScheduledUpdate()).abs().compareTo(Duration.ofSeconds(1)) < 1);
        assertTrue(Duration.between(writeStatus.tsLastPing(), readStatus.tsLastPing()).abs().compareTo(Duration.ofSeconds(1)) < 1);
        assertTrue(writeStatus.tsLastAvailable() != null && Duration.between(writeStatus.tsLastAvailable(), readStatus.tsLastAvailable()).abs().compareTo(Duration.ofSeconds(1)) < 1);
        assertTrue(writeStatus.tsLastError() != null && Duration.between(writeStatus.tsLastError(), readStatus.tsLastError()).abs().compareTo(Duration.ofSeconds(1)) < 1);
    }

    @Test
    void writeDnsObject() throws SQLException {
        var svc = new PingDao(dataSource);

        var dnsRecord = new DomainDnsRecord(null, "example.com", 2,
                List.of("test"),
                List.of("test2"),
                "test3",
                List.of("test4"),
                List.of("test5"),
                List.of("test6"),
                List.of("test7"),
                "test8",
                Instant.now(),
                Instant.now().plus(3600, ChronoUnit.SECONDS),
                4);

        svc.write(dnsRecord);

        var readDnsRecord = svc.getDomainDnsRecord("example.com");
        assertNotNull(readDnsRecord);
        assertEquals(dnsRecord.rootDomainName(), readDnsRecord.rootDomainName());
        assertEquals(dnsRecord.aaaaRecords(), readDnsRecord.aaaaRecords());
        assertEquals(dnsRecord.aRecords(), readDnsRecord.aRecords());
        assertEquals(dnsRecord.cnameRecord(), readDnsRecord.cnameRecord());
        assertEquals(dnsRecord.mxRecords(), readDnsRecord.mxRecords());
        assertEquals(dnsRecord.caaRecords(), readDnsRecord.caaRecords());
        assertEquals(dnsRecord.txtRecords(), readDnsRecord.txtRecords());
        assertEquals(dnsRecord.nsRecords(), readDnsRecord.nsRecords());
        assertEquals(dnsRecord.soaRecord(), readDnsRecord.soaRecord());
        assertTrue(Duration.between(dnsRecord.tsLastUpdate(), readDnsRecord.tsLastUpdate()).abs().compareTo(Duration.ofSeconds(1)) < 1);
        assertTrue(Duration.between(dnsRecord.tsNextScheduledUpdate(), readDnsRecord.tsNextScheduledUpdate()).abs().compareTo(Duration.ofSeconds(1)) < 1);
        assertEquals(dnsRecord.dnsCheckPriority(), readDnsRecord.dnsCheckPriority());


        // Update the DNS record

        var dnsRecord2 = new DomainDnsRecord(readDnsRecord.dnsRootDomainId(),
                "example.com", 2,
                List.of("btest"),
                List.of("ctest2"),
                "tdcest3",
                List.of("tedst4"),
                List.of("dtest5"),
                List.of("dtest6"),
                List.of("tdest7"),
                "tesat8",
                Instant.now(),
                Instant.now().plus(3600, ChronoUnit.SECONDS),
                4);

        svc.write(dnsRecord2);

        var readDnsRecord2 = svc.getDomainDnsRecord(readDnsRecord.dnsRootDomainId());

        assertNotNull(readDnsRecord2);
        assertEquals(dnsRecord2.dnsRootDomainId(), readDnsRecord2.dnsRootDomainId());
        assertEquals(dnsRecord2.rootDomainName(), readDnsRecord2.rootDomainName());
        assertEquals(dnsRecord2.aaaaRecords(), readDnsRecord2.aaaaRecords());
        assertEquals(dnsRecord2.aRecords(), readDnsRecord2.aRecords());
        assertEquals(dnsRecord2.cnameRecord(), readDnsRecord2.cnameRecord());
        assertEquals(dnsRecord2.mxRecords(), readDnsRecord2.mxRecords());
        assertEquals(dnsRecord2.caaRecords(), readDnsRecord2.caaRecords());
        assertEquals(dnsRecord2.txtRecords(), readDnsRecord2.txtRecords());
        assertEquals(dnsRecord2.nsRecords(), readDnsRecord2.nsRecords());
        assertEquals(dnsRecord2.soaRecord(), readDnsRecord2.soaRecord());
        assertTrue(Duration.between(dnsRecord2.tsLastUpdate(), readDnsRecord2.tsLastUpdate()).abs().compareTo(Duration.ofSeconds(1)) < 1);
        assertTrue(Duration.between(dnsRecord2.tsNextScheduledUpdate(), readDnsRecord2.tsNextScheduledUpdate()).abs().compareTo(Duration.ofSeconds(1)) < 1);
        assertEquals(dnsRecord2.dnsCheckPriority(), readDnsRecord2.dnsCheckPriority());

    }

    @Test
    public void readWriteSecurityObject() throws SQLException {
        DomainSecurityRecord foo = DomainSecurityRecord.builder()
                .domainId(1)
                .nodeId(2)
                .httpSchema(HttpSchema.HTTPS)
                .httpVersion("1.1")
                .httpCompression("gzip")
                .httpCacheControl("no-cache")
                .sslCertNotBefore(Instant.parse("2023-01-01T00:00:00Z"))
                .sslCertNotAfter(Instant.parse("2024-01-01T00:00:00Z"))
                .sslCertIssuer("Example CA")
                .sslCertSubject("example.com")
                .sslCertSerialNumber("1234567890")
                .sslCertPublicKeyHash(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20})
                .sslCertFingerprintSha256(new byte[]{0x02, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20})
                .sslCertSan("example.com,www.example.com")
                .sslCertWildcard(false)
                .sslProtocol("TLSv1.2")
                .sslCipherSuite("ECDHE-RSA-AES128-GCM-SHA256")
                .sslKeyExchange("ECDHE_RSA")
                .sslCertificateChainLength(3)
                .sslCertificateValid(true)
                .headerCorsAllowOrigin("*")
                .headerCorsAllowCredentials(true)
                .headerContentSecurityPolicyHash(40)
                .headerStrictTransportSecurity("max-age=31536000; includeSubDomains; preload")
                .headerReferrerPolicy("no-referrer-when-downgrade")
                .headerXFrameOptions("SAMEORIGIN")
                .headerXContentTypeOptions("nosniff")
                .headerXXssProtection("1; mode=block")
                .headerServer("Apache/2.4.41 (Ubuntu)")
                .headerXPoweredBy("PHP/7.4.3")
                .tsLastUpdate(Instant.now())
                .build();
        var svc = new PingDao(dataSource);
        svc.write(foo);

        var readFoo = svc.getDomainSecurityInformation(1);
        assertNotNull(readFoo);
        assertEquals(foo.domainId(), readFoo.domainId());
        assertEquals(foo.nodeId(), readFoo.nodeId());
        assertEquals(foo.asn(), readFoo.asn());
        assertEquals(foo.httpSchema(), readFoo.httpSchema());
        assertEquals(foo.httpVersion(), readFoo.httpVersion());
        assertEquals(foo.httpCompression(), readFoo.httpCompression());
        assertEquals(foo.httpCacheControl(), readFoo.httpCacheControl());
        assertEquals(foo.sslCertNotBefore(), readFoo.sslCertNotBefore());
        assertEquals(foo.sslCertNotAfter(), readFoo.sslCertNotAfter());
        assertEquals(foo.sslCertIssuer(), readFoo.sslCertIssuer());
        assertEquals(foo.sslCertSubject(), readFoo.sslCertSubject());
        assertEquals(foo.sslCertSerialNumber(), readFoo.sslCertSerialNumber());
        assertArrayEquals(foo.sslCertPublicKeyHash(), readFoo.sslCertPublicKeyHash());
        assertArrayEquals(foo.sslCertFingerprintSha256(), readFoo.sslCertFingerprintSha256());
        assertEquals(foo.sslCertSan(), readFoo.sslCertSan());
        assertEquals(foo.sslCertWildcard(), readFoo.sslCertWildcard());
        assertEquals(foo.sslProtocol(), readFoo.sslProtocol());
        assertEquals(foo.sslCipherSuite(), readFoo.sslCipherSuite());
        assertEquals(foo.sslKeyExchange(), readFoo.sslKeyExchange());
        assertEquals(foo.sslCertificateChainLength(), readFoo.sslCertificateChainLength());
        assertEquals(foo.sslCertificateValid(), readFoo.sslCertificateValid());
        assertEquals(foo.headerCorsAllowOrigin(), readFoo.headerCorsAllowOrigin());
        assertEquals(foo.headerCorsAllowCredentials(), readFoo.headerCorsAllowCredentials());
        assertEquals(foo.headerContentSecurityPolicyHash(), readFoo.headerContentSecurityPolicyHash());
        assertEquals(foo.headerStrictTransportSecurity(), readFoo.headerStrictTransportSecurity());
        assertEquals(foo.headerReferrerPolicy(), readFoo.headerReferrerPolicy());
        assertEquals(foo.headerXFrameOptions(), readFoo.headerXFrameOptions());
        assertEquals(foo.headerXContentTypeOptions(), readFoo.headerXContentTypeOptions());
        assertEquals(foo.headerXXssProtection(), readFoo.headerXXssProtection());
        assertEquals(foo.headerServer(), readFoo.headerServer());
        assertEquals(foo.headerXPoweredBy(), readFoo.headerXPoweredBy());
        assertTrue(foo.tsLastUpdate() != null && Duration.between(foo.tsLastUpdate(), readFoo.tsLastUpdate()).abs().compareTo(Duration.ofSeconds(1)) < 1);

    }

    @Test
    public void testWriteDomainAvailabilityEvent() {
        var event = new DomainAvailabilityEvent(
                1,
                2,
                true,
                TIMEOUT,
                200,
                "No error",
                Instant.now()
        );

        var svc = new PingDao(dataSource);
        svc.write(event);
    }

    @Test
    public void testWriteDomainSecurityEvent() {
        JsonObject<DomainSecurityRecord> signBefore = new JsonObject<>(
                DomainSecurityRecord.builder().domainId(1).build()
        );
        JsonObject<DomainSecurityRecord> signAfter = new JsonObject<>(
                DomainSecurityRecord.builder().domainId(1).build()
        );

        var event = new DomainSecurityEvent(
                1,
                2,
                Instant.now(),
                true,
                false,
                true,
                false,
                true,
                Duration.ofDays(30),
                false,
                false,
                true,
                signBefore,
                signAfter
        );

        var svc = new PingDao(dataSource);
        svc.write(event);
    }

    @Test
    void write() {
        var dnsEvent = new DomainDnsEvent(
                1,
                2,
                Instant.now(),
                true,
                false,
                true,
                false,
                true,
                false,
                false,
                false,
                new JsonObject<>(new DomainDnsRecord(null, "example.com", 2, List.of("test"), List.of("test2"), "test3", List.of("test4"), List.of("test5"), List.of("test6"), List.of("test7"), "test8", Instant.now(), Instant.now().plus(3600, ChronoUnit.SECONDS), 4)),
                new JsonObject<>(new DomainDnsRecord(null, "example.com", 2, List.of("btest"), List.of("ctest2"), "tdcest3", List.of("tedst4"), List.of("dtest5"), List.of("dtest6"), List.of("tdest7"), "tesat8", Instant.now(), Instant.now().plus(3600, ChronoUnit.SECONDS), 4))
        );
        var svc = new PingDao(dataSource);
        svc.write(dnsEvent);
    }
}
