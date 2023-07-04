package nu.marginalia.control.model;

public record EventLogEntry(
        String serviceName,
        String instance,
        String eventTime,
        String eventType,
        String eventMessage)
{
}
