package nu.marginalia.wmsa.api.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ApiLicense {
    @NonNull
    public String key;
    @NonNull
    public String license;
    @NonNull
    public String name;
    public int rate;
}
