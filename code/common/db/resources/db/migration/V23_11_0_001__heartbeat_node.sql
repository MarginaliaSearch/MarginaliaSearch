ALTER TABLE TASK_HEARTBEAT ADD COLUMN NODE INT NOT NULL DEFAULT -1;
ALTER TABLE PROCESS_HEARTBEAT ADD COLUMN NODE INT NOT NULL DEFAULT -1;
ALTER TABLE SERVICE_HEARTBEAT ADD COLUMN NODE INT NOT NULL DEFAULT -1;
