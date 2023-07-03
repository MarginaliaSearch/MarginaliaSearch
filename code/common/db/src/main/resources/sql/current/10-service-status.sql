CREATE TABLE PROC_SERVICE_HEARTBEAT(
    SERVICE_NAME VARCHAR(255) PRIMARY KEY COMMENT 'Full name of the service, including node id if applicable, e.g. search-service:0',
    SERVICE_BASE VARCHAR(255) NOT NULL COMMENT 'Base name of the service, e.g. search-service',
    INSTANCE VARCHAR(255) NOT NULL COMMENT 'UUID of the service instance',
    ALIVE BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Set to false when the service is doing an orderly shutdown',
    HEARTBEAT_TIME TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Service was last seen at this point'
);

CREATE TABLE PROC_SERVICE_EVENTLOG(
    ID BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique id',
    SERVICE_NAME VARCHAR(255) NOT NULL COMMENT 'Full name of the service, including node id if applicable, e.g. search-service:0',
    SERVICE_BASE VARCHAR(255) NOT NULL COMMENT 'Base name of the service, e.g. search-service',
    INSTANCE VARCHAR(255) NOT NULL COMMENT 'UUID of the service instance',
    EVENT_TIME TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Event time',
    EVENT_TYPE VARCHAR(255) NOT NULL COMMENT 'Event type',
    EVENT_MESSAGE VARCHAR(255) NOT NULL COMMENT 'Event message'
);