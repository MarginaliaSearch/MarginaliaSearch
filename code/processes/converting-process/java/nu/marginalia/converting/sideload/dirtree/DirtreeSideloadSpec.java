package nu.marginalia.converting.sideload.dirtree;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
class DirtreeSideloadSpec {
    public String name;
    public String domainName;
    public String dir;
    public String baseUrl;
    public List<String> keywords;
}
