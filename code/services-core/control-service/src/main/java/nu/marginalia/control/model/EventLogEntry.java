package nu.marginalia.control.model;

public record EventLogEntry(
        long id,
        String serviceName,
        String instanceFull,
        String eventTime,
        String eventDateTime,
        String eventType,
        String eventMessage)
{
    public String instance() {
        return instanceFull.substring(0, 8);
    }
    public String instanceColor() {
        return '#' + instanceFull.substring(0, 6);
    }
    public String instanceColor2() {
        return '#' + instanceFull.substring(25, 31);
    }
}
