package nu.marginalia.wmsa.edge.assistant.dict;

import lombok.AllArgsConstructor;

import javax.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@AllArgsConstructor
public class WikiSearchResult {
    private final String name;
    @Nullable
    private final String refName;

    public String getName() {
        return name.replace('_', ' ');
    }
    @Nullable
    public String getRefName() {
        if (refName == null)
            return null;

        return refName.replace('_', ' ');
    }

    public String getUrl() {
        return "https://encyclopedia.marginalia.nu/wiki/" + URLEncoder.encode(getRealName(), StandardCharsets.UTF_8);
    }

    public String getRealName() {
        return Optional.ofNullable(refName).orElse(name);
    }

    public String getInternalName() {
        return name;
    }

    @Override
    public int hashCode() {
        return getRealName().hashCode();
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof WikiSearchResult) {
            WikiSearchResult r = (WikiSearchResult) other;
            return r.getRealName().equals(getRealName());
        }
        return false;
    }

}
