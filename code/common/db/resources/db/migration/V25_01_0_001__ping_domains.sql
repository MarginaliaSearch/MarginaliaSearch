
-- Create metadata tables for domain ping status and security information

-- These are not ICMP pings, but rather HTTP(S) pings to check the availability and security
-- of web servers associated with domains, to assess uptime and changes in security configurations
-- indicating ownership changes or security issues.

-- Note: DOMAIN_ID and NODE_ID are used to identify the domain and the node that performed the ping.
-- These are strictly speaking foreign keys to the EC_DOMAIN table, but as it
-- is strictly append-only, we do not need to enforce foreign key constraints.

CREATE TABLE IF NOT EXISTS DOMAIN_AVAILABILITY_INFORMATION (
    DOMAIN_ID INT NOT NULL PRIMARY KEY,
    NODE_ID INT NOT NULL,

    SERVER_AVAILABLE BOOLEAN NOT NULL,  -- Indicates if the server is available (true) or not (false)
    SERVER_IP VARBINARY(16),            -- IP address of the server (IPv4 or IPv6)
    SERVER_IP_ASN INTEGER,              -- Autonomous System number

    DATA_HASH BIGINT,                   -- Hash of the data for integrity checks
    SECURITY_CONFIG_HASH BIGINT,        -- Hash of the security configuration for integrity checks

    HTTP_SCHEMA ENUM('HTTP', 'HTTPS'),  -- HTTP or HTTPS protocol used
    HTTP_ETAG VARCHAR(255),             -- ETag of the resource as per HTTP headers
    HTTP_LAST_MODIFIED VARCHAR(255),    -- Last modified date of the resource as per HTTP headers
    HTTP_STATUS INT,                    -- HTTP status code (e.g., 200, 404, etc.)
    HTTP_LOCATION VARCHAR(255),         -- If the server redirects, this is the location of the redirect
    HTTP_RESPONSE_TIME_MS SMALLINT UNSIGNED, -- Response time in milliseconds

    ICMP_PING_TIME_MS SMALLINT UNSIGNED, -- ICMP ping time in milliseconds (if available)

    ERROR_CLASSIFICATION ENUM('NONE', 'TIMEOUT', 'SSL_ERROR', 'DNS_ERROR', 'CONNECTION_ERROR', 'HTTP_CLIENT_ERROR', 'HTTP_SERVER_ERROR', 'UNKNOWN'), -- Classification of the error if the server is not available
    ERROR_MESSAGE VARCHAR(255),         -- Error message if the server is not available

    TS_LAST_PING TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Timestamp of the last ping
    TS_LAST_AVAILABLE TIMESTAMP,        -- Timestamp of the last time the server was available
    TS_LAST_ERROR TIMESTAMP,             -- Timestamp of the last error encountered

    NEXT_SCHEDULED_UPDATE TIMESTAMP NOT NULL,
    BACKOFF_CONSECUTIVE_FAILURES INT NOT NULL DEFAULT 0, -- Number of consecutive failures to ping the server
    BACKOFF_FETCH_INTERVAL INT NOT NULL DEFAULT 60 -- Interval in seconds for the next scheduled ping
) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX IF NOT EXISTS DOMAIN_AVAILABILITY_INFORMATION__NODE_ID__DOMAIN_ID_IDX ON DOMAIN_AVAILABILITY_INFORMATION (NODE_ID, DOMAIN_ID);
CREATE INDEX IF NOT EXISTS DOMAIN_AVAILABILITY_INFORMATION__NEXT_SCHEDULED_UPDATE_IDX ON DOMAIN_AVAILABILITY_INFORMATION (NODE_ID, NEXT_SCHEDULED_UPDATE);



CREATE TABLE IF NOT EXISTS DOMAIN_SECURITY_INFORMATION (
    DOMAIN_ID INT NOT NULL PRIMARY KEY,
    NODE_ID INT NOT NULL,

    ASN INTEGER,                     -- Autonomous System Number (ASN) of the server
    HTTP_SCHEMA ENUM('HTTP', 'HTTPS'),  -- HTTP or HTTPS protocol used
    HTTP_VERSION VARCHAR(10),           -- HTTP version used (e.g., HTTP/1.1, HTTP/2)
    HTTP_COMPRESSION VARCHAR(50),       -- Compression method used (e.g., gzip, deflate, br)
    HTTP_CACHE_CONTROL TEXT,            -- Cache control directives from HTTP headers

    SSL_CERT_NOT_BEFORE TIMESTAMP,         -- Valid from date (usually same as issued)
    SSL_CERT_NOT_AFTER TIMESTAMP,          -- Valid until date (usually same as expires)

    SSL_CERT_ISSUER VARCHAR(255),         -- CA that issued the cert
    SSL_CERT_SUBJECT VARCHAR(255),        -- Certificate subject/CN

    SSL_CERT_PUBLIC_KEY_HASH BINARY(32),     -- SHA-256 hash of the public key
    SSL_CERT_SERIAL_NUMBER VARCHAR(100),     -- Unique cert serial number
    SSL_CERT_FINGERPRINT_SHA256 BINARY(32),  -- SHA-256 fingerprint for exact identification
    SSL_CERT_SAN TEXT,                       -- Subject Alternative Names (JSON array)
    SSL_CERT_WILDCARD BOOLEAN,               -- Wildcard certificate (*.example.com)

    SSL_PROTOCOL VARCHAR(20),             -- TLS 1.2, TLS 1.3, etc.
    SSL_CIPHER_SUITE VARCHAR(100),        -- e.g., TLS_AES_256_GCM_SHA384
    SSL_KEY_EXCHANGE VARCHAR(50),         -- ECDHE, RSA, etc.
    SSL_CERTIFICATE_CHAIN_LENGTH TINYINT, -- Number of certs in chain

    SSL_CERTIFICATE_VALID BOOLEAN,        -- Valid cert chain

    HEADER_CORS_ALLOW_ORIGIN TEXT,               -- Could be *, specific domains, or null
    HEADER_CORS_ALLOW_CREDENTIALS BOOLEAN,       -- Credential handling
    HEADER_CONTENT_SECURITY_POLICY_HASH INT,     -- CSP header, hash of the policy
    HEADER_STRICT_TRANSPORT_SECURITY VARCHAR(255), -- HSTS header
    HEADER_REFERRER_POLICY VARCHAR(50),          -- Referrer handling
    HEADER_X_FRAME_OPTIONS VARCHAR(50),          -- Clickjacking protection
    HEADER_X_CONTENT_TYPE_OPTIONS VARCHAR(50),   -- MIME sniffing protection
    HEADER_X_XSS_PROTECTION VARCHAR(50),         -- XSS protection header

    HEADER_SERVER VARCHAR(255),                 -- Server header (e.g., Apache, Nginx, etc.)
    HEADER_X_POWERED_BY VARCHAR(255),           -- X-Powered-By header (if present)

    TS_LAST_UPDATE TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP -- Timestamp of the last SSL check
) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;


CREATE INDEX IF NOT EXISTS DOMAIN_SECURITY_INFORMATION__NODE_ID__DOMAIN_ID_IDX ON DOMAIN_SECURITY_INFORMATION (NODE_ID, DOMAIN_ID);

CREATE TABLE IF NOT EXISTS DOMAIN_SECURITY_EVENTS (
    CHANGE_ID BIGINT AUTO_INCREMENT PRIMARY KEY, -- Unique identifier for the change
    DOMAIN_ID INT NOT NULL, -- Domain ID, used as a foreign key to EC_DOMAIN
    NODE_ID INT NOT NULL,

    TS_CHANGE TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Timestamp of the change

    CHANGE_ASN BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to ASN (Autonomous System Number)
    CHANGE_CERTIFICATE_FINGERPRINT BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to SSL certificate fingerprint
    CHANGE_CERTIFICATE_PROFILE BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to SSL certificate profile (e.g., algorithm, exchange)
    CHANGE_CERTIFICATE_SAN BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to SSL certificate SAN (Subject Alternative Name)
    CHANGE_CERTIFICATE_PUBLIC_KEY BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to SSL certificate public key
    CHANGE_SECURITY_HEADERS BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to security headers
    CHANGE_IP_ADDRESS BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to IP address
    CHANGE_SOFTWARE BOOLEAN  NOT NULL DEFAULT FALSE, -- Indicates if the change is related to the generator (e.g., web server software)
    OLD_CERT_TIME_TO_EXPIRY INT, -- Time to expiry of the old certificate in hours, if applicable

    SECURITY_SIGNATURE_BEFORE BLOB NOT NULL, -- Security signature before the change, gzipped json record
    SECURITY_SIGNATURE_AFTER BLOB NOT NULL  -- Security signature after the change, gzipped json record
) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX IF NOT EXISTS DOMAIN_SECURITY_EVENTS__NODE_ID__DOMAIN_ID_IDX ON DOMAIN_SECURITY_EVENTS (NODE_ID, DOMAIN_ID);
CREATE INDEX IF NOT EXISTS DOMAIN_SECURITY_EVENTS__TS_CHANGE_IDX ON DOMAIN_SECURITY_EVENTS (TS_CHANGE);

CREATE TABLE IF NOT EXISTS DOMAIN_AVAILABILITY_EVENTS (
    AVAILABILITY_RECORD_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    DOMAIN_ID INT NOT NULL,
    NODE_ID INT NOT NULL,

    AVAILABLE BOOLEAN NOT NULL, -- True if the service is available, false if it is not
    OUTAGE_TYPE ENUM('NONE', 'TIMEOUT', 'SSL_ERROR', 'DNS_ERROR', 'CONNECTION_ERROR', 'HTTP_CLIENT_ERROR', 'HTTP_SERVER_ERROR', 'UNKNOWN') NOT NULL,
    HTTP_STATUS_CODE INT, -- HTTP status code if available (e.g., 200, 404, etc.)
    ERROR_MESSAGE VARCHAR(255),       -- Specific error details

    TS_UPDATE TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP -- Timestamp of the last update
) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX DOMAIN_AVAILABILITY_EVENTS__DOMAIN_ID_TS_IDX ON DOMAIN_AVAILABILITY_EVENTS (DOMAIN_ID, TS_UPDATE);
CREATE INDEX DOMAIN_AVAILABILITY_EVENTS__TS_UPDATE_IDX ON DOMAIN_AVAILABILITY_EVENTS (TS_UPDATE);

CREATE TABLE IF NOT EXISTS DOMAIN_DNS_INFORMATION (
    DNS_ROOT_DOMAIN_ID INT AUTO_INCREMENT PRIMARY KEY,
    ROOT_DOMAIN_NAME VARCHAR(255) NOT NULL UNIQUE,
    NODE_AFFINITY INT NOT NULL,              -- Node ID that performs the DNS check, assign randomly across nodes

    DNS_A_RECORDS TEXT,                      -- JSON array of IPv4 addresses
    DNS_AAAA_RECORDS TEXT,                   -- JSON array of IPv6 addresses
    DNS_CNAME_RECORD VARCHAR(255),           -- Canonical name (if applicable)
    DNS_MX_RECORDS TEXT,                     -- JSON array of mail exchange records
    DNS_CAA_RECORDS TEXT,                    -- Certificate Authority Authorization
    DNS_TXT_RECORDS TEXT,                    -- TXT records (SPF, DKIM, verification, etc.)
    DNS_NS_RECORDS TEXT,                     -- Name servers (JSON array)
    DNS_SOA_RECORD TEXT,                     -- Start of Authority (JSON object)

    TS_LAST_DNS_CHECK TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    TS_NEXT_DNS_CHECK TIMESTAMP NOT NULL,
    DNS_CHECK_PRIORITY TINYINT DEFAULT 0    -- Priority of the DNS check, in case we want to schedule a refresh sooner
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX DOMAIN_DNS_INFORMATION__PRIORITY_NEXT_CHECK_IDX ON DOMAIN_DNS_INFORMATION (NODE_AFFINITY, DNS_CHECK_PRIORITY DESC, TS_NEXT_DNS_CHECK);

CREATE TABLE IF NOT EXISTS DOMAIN_DNS_EVENTS (
     DNS_EVENT_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
     DNS_ROOT_DOMAIN_ID INT NOT NULL,
     NODE_ID INT NOT NULL,

     TS_CHANGE TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

-- DNS change type flags
     CHANGE_A_RECORDS BOOLEAN NOT NULL DEFAULT FALSE,        -- IPv4 address changes
     CHANGE_AAAA_RECORDS BOOLEAN NOT NULL DEFAULT FALSE,     -- IPv6 address changes
     CHANGE_CNAME BOOLEAN NOT NULL DEFAULT FALSE,            -- CNAME changes
     CHANGE_MX_RECORDS BOOLEAN NOT NULL DEFAULT FALSE,       -- Mail server changes
     CHANGE_CAA_RECORDS BOOLEAN NOT NULL DEFAULT FALSE,      -- Certificate authority changes
     CHANGE_TXT_RECORDS BOOLEAN NOT NULL DEFAULT FALSE,      -- TXT record changes (SPF, DKIM, etc.)
     CHANGE_NS_RECORDS BOOLEAN NOT NULL DEFAULT FALSE,       -- Name server changes (big red flag!)
     CHANGE_SOA_RECORD BOOLEAN NOT NULL DEFAULT FALSE,       -- Start of Authority changes

     DNS_SIGNATURE_BEFORE BLOB NOT NULL,  -- Compressed JSON snapshot of DNS records before change
     DNS_SIGNATURE_AFTER BLOB NOT NULL    -- Compressed JSON snapshot of DNS records after change
) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX DOMAIN_DNS_EVENTS__DNS_ROOT_DOMAIN_ID_TS_IDX ON DOMAIN_DNS_EVENTS (DNS_ROOT_DOMAIN_ID, TS_CHANGE);
CREATE INDEX DOMAIN_DNS_EVENTS__TS_CHANGE_IDX ON DOMAIN_DNS_EVENTS (TS_CHANGE);