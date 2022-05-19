package nu.marginalia.wmsa.smhi.model.index;

import lombok.Getter;
import nu.marginalia.wmsa.smhi.model.Plats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class IndexPlatser {
    List<IndexPlats> platserPerNyckel = new ArrayList<>();

    public IndexPlatser(List<Plats> platser) {
        var platsMap = kategoriseraEfterNyckel(platser);

        platsMap.keySet().stream().sorted()
                .forEach(p -> platserPerNyckel.add(new IndexPlats(p, platsMap.get(p))));
    }

    private Map<String, List<Plats>> kategoriseraEfterNyckel(List<Plats> platser) {
        return platser.stream().collect(
                    Collectors.groupingBy(p ->
                         p.namn.substring(0, 1)
                            .toUpperCase()));
    }
}
