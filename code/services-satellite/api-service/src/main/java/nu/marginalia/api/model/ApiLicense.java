package nu.marginalia.api.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ApiLicense {
    /** Key ID */
    @NonNull
    public String key;

    /** License terms */
    @NonNull
    public String license;

    /** License holder name */
    @NonNull
    public String name;

    /** Requests per minute. If zero or less, unrestricted. */
    public int rate;
}
