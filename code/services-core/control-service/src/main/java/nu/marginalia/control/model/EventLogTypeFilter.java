package nu.marginalia.control.model;

public record EventLogTypeFilter(
        String name,
        String value,
        boolean current
)
{
}
