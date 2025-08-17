package nu.marginalia.converting.model;

import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
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
    public DocumentFormat format;

    public List<EdgeUrl> linksInternal;
    public List<EdgeUrl> linksExternal;

    public DocumentMetadata metadata;
    public GeneratorType generator;
    public String language;

    public String toString() {
        return "ProcessedDocumentDetails(title=" + this.title + ", description=" + this.description + ", pubYear=" + this.pubYear + ", length=" + this.length + ", quality=" + this.quality + ", hashCode=" + this.hashCode + ", features=" + this.features + ", standard=" + this.format + ", linksInternal=" + this.linksInternal + ", linksExternal=" + this.linksExternal + ", metadata=" + this.metadata + ", generator=" + this.generator + ")";
    }
}
