package nu.marginalia.wmsa.edge.converting.processor.logic;

import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

public class FeedExtractor {
    private final LinkParser linkParser;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public FeedExtractor(LinkParser linkParser) {
        this.linkParser = linkParser;
    }

    public Optional<EdgeUrl> getFeedFromAlternateTag(EdgeUrl crawlUrl, Element alternateTag) {
        var type = alternateTag.attr("type");
        if (type == null) {
            return Optional.empty();
        }

        try {
            var url = linkParser.parseLink(crawlUrl, alternateTag.attr("href"));

            if (url.isEmpty())
                return Optional.empty();

            if (!Objects.equals(crawlUrl.domain, url.get().domain))
                return Optional.empty();

            if ("application/atom+xml".equalsIgnoreCase(type)) {
                return url;
            }

            if ("application/rss+xml".equalsIgnoreCase(type)) {
                return url;
            }

            if ("application/rdf+xml".equalsIgnoreCase(type)) {
                return url;
            }


        }
        catch (Exception ex) {
            logger.debug("Bad URI syntax", ex);
        }
        return Optional.empty();
    }


}
