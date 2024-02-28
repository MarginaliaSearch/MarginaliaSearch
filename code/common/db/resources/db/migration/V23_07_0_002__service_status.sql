CREATE TABLE IF NOT EXISTS SERVICE_HEARTBEAT (
    SERVICE_NAME VARCHAR(255) PRIMARY KEY COMMENT "Full name of the service, including node id if applicable, e.g. search-service:0",
    SERVICE_BASE VARCHAR(255) NOT NULL COMMENT "Base name of the service, e.g. search-service",
    INSTANCE VARCHAR(255) NOT NULL COMMENT "UUID of the service instance",
    ALIVE BOOLEAN NOT NULL DEFAULT TRUE COMMENT "Set to false when the service is doing an orderly shutdown",
    HEARTBEAT_TIME TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT "Service was last seen at this point"
);

CREATE TABLE IF NOT EXISTS PROCESS_HEARTBEAT (
    PROCESS_NAME VARCHAR(255) PRIMARY KEY COMMENT "Full name of the process, including node id if applicable, e.g. converter:0",
    PROCESS_BASE VARCHAR(255) NOT NULL COMMENT "Base name of the process, e.g. converter",
    INSTANCE VARCHAR(255) NOT NULL COMMENT "UUID of the process instance",
    STATUS ENUM ('STARTING', 'RUNNING', 'STOPPED') NOT NULL DEFAULT 'STARTING' COMMENT "Status of the process",
    PROGRESS INT NOT NULL DEFAULT 0 COMMENT "Progress of the process",
    HEARTBEAT_TIME TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT "Process was last seen at this point"
);

CREATE TABLE IF NOT EXISTS SERVICE_EVENTLOG(
    ID BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT "Unique id",
    SERVICE_NAME VARCHAR(255) NOT NULL COMMENT "Full name of the service, including node id if applicable, e.g. search-service:0",
    SERVICE_BASE VARCHAR(255) NOT NULL COMMENT "Base name of the service, e.g. search-service",
    INSTANCE VARCHAR(255) NOT NULL COMMENT "UUID of the service instance",
    EVENT_TIME TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT "Event time",
    EVENT_TYPE VARCHAR(255) NOT NULL COMMENT "Event type",
    EVENT_MESSAGE VARCHAR(255) NOT NULL COMMENT "Event message"
);

