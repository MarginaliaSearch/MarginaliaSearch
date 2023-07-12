CREATE TABLE IF NOT EXISTS MESSAGE_QUEUE (
    ID              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique id',
    RELATED_ID      BIGINT NOT NULL DEFAULT -1        COMMENT 'Unique id a related message',
    SENDER_INBOX    VARCHAR(255)          COMMENT 'Name of the sender inbox',
    RECIPIENT_INBOX VARCHAR(255) NOT NULL COMMENT 'Name of the recipient inbox',
    FUNCTION        VARCHAR(255) NOT NULL COMMENT 'Which function to run',
    PAYLOAD         TEXT                  COMMENT 'Message to recipient',
    -- These fields are used to avoid double processing of messages
    -- instance marks the unique instance of the party, and the tick marks
    -- the current polling iteration.  Both are necessary.
    OWNER_INSTANCE  VARCHAR(255)          COMMENT 'Instance UUID corresponding to the party that has claimed the message',
    OWNER_TICK      BIGINT  DEFAULT -1    COMMENT 'Used by recipient to determine which messages it has processed',
    STATE           ENUM('NEW', 'ACK', 'OK', 'ERR', 'DEAD')
                    NOT NULL DEFAULT 'NEW' COMMENT 'Processing state',
    CREATED_TIME    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Time of creation',
    UPDATED_TIME    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Time of last update',
    TTL             INT              COMMENT 'Time to live in seconds'
);
