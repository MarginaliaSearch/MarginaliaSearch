package nu.marginalia.control.model;

public enum Actor {
    RECONVERT_LOAD,
    CONVERTER_MONITOR,
    LOADER_MONITOR,
    MESSAGE_QUEUE_MONITOR,
    PROCESS_LIVENESS_MONITOR,
    FILE_STORAGE_MONITOR
    ;


    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
