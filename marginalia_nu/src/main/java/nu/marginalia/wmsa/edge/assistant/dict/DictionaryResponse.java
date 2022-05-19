package nu.marginalia.wmsa.edge.assistant.dict;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@ToString @Getter @AllArgsConstructor @NoArgsConstructor
public class DictionaryResponse {
    public String word;
    public List<DictionaryEntry> entries;
}
