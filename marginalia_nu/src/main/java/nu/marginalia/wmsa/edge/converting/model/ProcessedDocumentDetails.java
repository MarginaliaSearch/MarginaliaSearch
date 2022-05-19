package nu.marginalia.wmsa.edge.converting.model;

import lombok.ToString;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlFeature;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;

import java.util.List;
import java.util.Set;

@ToString
public class ProcessedDocumentDetails {
    public String title;
    public String description;

    public int length;
    public double quality;
    public long hashCode;

    public Set<HtmlFeature> features;
    public EdgeHtmlStandard standard;

    public List<EdgeUrl> linksInternal;
    public List<EdgeUrl> linksExternal;
    public List<EdgeUrl> feedLinks;
}
