package nu.marginalia.wmsa.edge.assistant.dict;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@ToString @Getter
public class WikiArticles {
    public List<String> entries;

    public WikiArticles(String... args) {
        entries = List.of(args);
    }
    public String getPage() {
        if (entries.isEmpty()) {
            return null;
        }
        else {
            return entries.get(0);
        }
    }
}
