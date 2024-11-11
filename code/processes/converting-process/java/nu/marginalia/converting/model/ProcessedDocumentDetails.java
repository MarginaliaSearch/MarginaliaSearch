package nu.marginalia.converting.model;

import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.html.HtmlStandard;
import nu.marginalia.model.idx.DocumentMetadata;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

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

    public String toString() {
        return "ProcessedDocumentDetails(title=" + this.title + ", description=" + this.description + ", pubYear=" + this.pubYear + ", length=" + this.length + ", quality=" + this.quality + ", hashCode=" + this.hashCode + ", features=" + this.features + ", standard=" + this.standard + ", linksInternal=" + this.linksInternal + ", linksExternal=" + this.linksExternal + ", feedLinks=" + this.feedLinks + ", metadata=" + this.metadata + ", generator=" + this.generator + ")";
    }
}
