package nu.marginalia.converting.sideload.dirtree;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor @NoArgsConstructor
@Setter @Getter
class DirtreeSideloadSpecList {
    public List<DirtreeSideloadSpec> sources;
}
