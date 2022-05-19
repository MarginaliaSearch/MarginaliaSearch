package nu.marginalia.wmsa.edge.integration.arxiv.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor @NoArgsConstructor
public class ArxivMetadata {
    public String id;
    public String submitter;
    public String authors;
    public String title;
    @SerializedName("abstract")
    public String _abstract;

    public String getAbstract() {
        return _abstract;
    }
}
