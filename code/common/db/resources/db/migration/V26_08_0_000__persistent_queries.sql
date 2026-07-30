CREATE TABLE IF NOT EXISTS PERSISTENT_QUERIES (
    INTERNAL_ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    PUBLIC_ID VARCHAR(255) NOT NULL UNIQUE, -- Public ID, something non-guessable
    CUSTOMER_ID VARCHAR(255) NOT NULL,      -- Owner of the query
    TERMS_INCLUDE TEXT,                     -- Query terms
    TERMS_EXCLUDE TEXT,                     -- Query terms
    LANG_ISO_CODE VARCHAR(10) NOT NULL,     -- Language subindex
    TS_ADDED   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- When we create
    TS_DISABLED TIMESTAMP                   -- Present when disabled
    )
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
