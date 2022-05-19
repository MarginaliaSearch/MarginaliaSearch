package nu.marginalia.wmsa.smhi.model.index;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.wmsa.smhi.model.Plats;

import java.util.List;

@Getter @AllArgsConstructor
public class IndexPlats {
    String nyckel;
    List<Plats> platser;
}
