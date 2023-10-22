package nu.marginalia.control.sys.model;

public record EventLogEntry(
        long id,
        String serviceName,
        String instanceFull,
        String eventTime,
        String eventDateTime,
        String eventType,
        String eventMessage)
{
}
