package nu.marginalia.control.sys.model;

public record EventLogServiceFilter(
        String name,
        String value,
        boolean current
)
{
}
