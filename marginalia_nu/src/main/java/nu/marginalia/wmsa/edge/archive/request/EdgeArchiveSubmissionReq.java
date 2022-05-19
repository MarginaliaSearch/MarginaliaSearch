package nu.marginalia.wmsa.edge.archive.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@AllArgsConstructor @Getter @ToString
public class EdgeArchiveSubmissionReq {
    EdgeUrl url;
    EdgeRawPageContents data;
}
