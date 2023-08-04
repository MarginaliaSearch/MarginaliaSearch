CREATE TABLE IF NOT EXISTS TASK_HEARTBEAT (
    TASK_NAME VARCHAR(255) PRIMARY KEY COMMENT "Full name of the task, including node id if applicable, e.g. reconvert:0",
    TASK_BASE VARCHAR(255) NOT NULL COMMENT "Base name of the task, e.g. reconvert",
    INSTANCE VARCHAR(255) NOT NULL COMMENT "UUID of the task instance",
    SERVICE_INSTANCE VARCHAR(255) NOT NULL COMMENT "UUID of the parent service",
    STATUS ENUM ('STARTING', 'RUNNING', 'STOPPED') NOT NULL DEFAULT 'STARTING' COMMENT "Status of the task",
    PROGRESS INT NOT NULL DEFAULT 0 COMMENT "Progress of the task",
    STAGE_NAME VARCHAR(255) DEFAULT "",
    HEARTBEAT_TIME TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT "Task was last seen at this point"
);
