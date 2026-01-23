package nu.marginalia.language;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Singleton
public class NounVariants {

    private final Map<String, List<String>> nounVariants = new HashMap<>(1_000_000);

    @Inject
    public NounVariants() throws IOException {
        try (var res = new InputStreamReader(ClassLoader.getSystemResourceAsStream("dictionary/noun_list.csv"))) {
            for (String pairStr: res.readAllLines()) {
                String[] parts = pairStr.split(",");
                if (parts.length == 2) {
                    nounVariants.computeIfAbsent(parts[0], (v) -> new ArrayList<>()).add(parts[1]);
                    nounVariants.computeIfAbsent(parts[1], (v) -> new ArrayList<>()).add(parts[0]);
                }
            }
        }
    }

    public List<String> pluralVariant(String term) {
        return nounVariants.getOrDefault(term, Collections.emptyList());
    }
}
