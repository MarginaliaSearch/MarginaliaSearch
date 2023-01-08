package nu.marginalia.wmsa.edge.converting.model;

import lombok.ToString;
import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

@ToString
public class ProcessedDocumentDetails {
    public String title;
    public String description;

    @Nullable
    public Integer pubYear;

    public int length;
    public double quality;
    public long hashCode;

    public Set<HtmlFeature> features;
    public EdgeHtmlStandard standard;

    public List<EdgeUrl> linksInternal;
    public List<EdgeUrl> linksExternal;
    public List<EdgeUrl> feedLinks;

    public EdgePageDocumentsMetadata metadata;
}
