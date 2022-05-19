package nu.marginalia.wmsa.edge.model.crawl;

import lombok.Data;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@Data
public class EdgeUrlVisit {
    public final EdgeUrl url;
    public final Integer data_hash_code;
    public final Double quality;
    public final String title;
    public final String description;
    public final String ipAddress;
    public final String format;
    public final int features;

    public final int wordCountDistinct;
    public final int wordCountTotal;

    public final EdgeUrlState urlState;
}
