package nu.marginalia.api.math.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class DictionaryEntry {
    public final String type;
    public final String word;
    public final String definition;
}
