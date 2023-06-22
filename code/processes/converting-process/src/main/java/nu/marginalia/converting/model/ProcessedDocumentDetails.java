package nu.marginalia.converting.model;

import lombok.ToString;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.EdgeUrl;

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
    public HtmlStandard standard;

    public List<EdgeUrl> linksInternal;
    public List<EdgeUrl> linksExternal;
    public List<EdgeUrl> feedLinks;

    public DocumentMetadata metadata;
    public GeneratorType generator;
}
