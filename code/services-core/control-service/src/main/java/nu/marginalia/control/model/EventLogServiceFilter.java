package nu.marginalia.control.model;

public record EventLogServiceFilter(
        String name,
        String value,
        boolean current
)
{
}
