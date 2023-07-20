package nu.marginalia.crawl.retreival;

import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/** A reference to a domain that has been crawled before. */
public class CrawlDataReference {
    private final Logger logger = LoggerFactory.getLogger(CrawlDataReference.class);
    final Map<EdgeUrl, CrawledDocument> documents;
    final Map<EdgeUrl, String> etags;
    final Map<EdgeUrl, String> lastModified;
    final Set<EdgeUrl> previouslyDeadUrls = new HashSet<>();

    CrawlDataReference(CrawledDomain referenceDomain) {

        if (referenceDomain == null || referenceDomain.doc == null) {
            documents = Collections.emptyMap();
            etags = Collections.emptyMap();
            lastModified = Collections.emptyMap();
            return;
        }

        documents = new HashMap<>(referenceDomain.doc.size());
        etags = new HashMap<>(referenceDomain.doc.size());
        lastModified = new HashMap<>(referenceDomain.doc.size());

        for (var doc : referenceDomain.doc) {
            try {
                addReference(doc);
            } catch (URISyntaxException ex) {
                logger.warn("Failed to add reference document {}", doc.url);
            }
        }
    }

    private void addReference(CrawledDocument doc) throws URISyntaxException {
        var url = new EdgeUrl(doc.url);

        if (doc.httpStatus == 404) {
            previouslyDeadUrls.add(url);
            return;
        }

        if (doc.httpStatus != 200) {
            return;
        }


        documents.put(url, doc);

        String headers = doc.headers;
        if (headers != null) {
            String[] headersLines = headers.split("\n");

            String lastmod = null;
            String etag = null;

            for (String line : headersLines) {
                if (line.toLowerCase().startsWith("etag:")) {
                    etag = line.substring(5).trim();
                }
                if (line.toLowerCase().startsWith("last-modified:")) {
                    lastmod = line.substring(14).trim();
                }
            }

            if (lastmod != null) {
                lastModified.put(url, lastmod);
            }
            if (etag != null) {
                etags.put(url, etag);
            }
        }
    }

    public boolean isPreviouslyDead(EdgeUrl url) {
        return previouslyDeadUrls.contains(url);
    }
    public int size() {
        return documents.size();
    }

    public String getEtag(EdgeUrl url) {
        return etags.get(url);
    }

    public String getLastModified(EdgeUrl url) {
        return lastModified.get(url);
    }

    public Map<EdgeUrl, CrawledDocument> allDocuments() {
        return documents;
    }


    public Map<EdgeUrl, CrawledDocument> sample(int sampleSize) {
        return documents.entrySet().stream().limit(sampleSize).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void evict() {
        documents.clear();
        etags.clear();
        lastModified.clear();
    }

    public CrawledDocument getDoc(EdgeUrl top) {
        return documents.get(top);
    }

    // This bit of manual housekeeping is needed to keep the memory footprint low
    public void dispose(EdgeUrl url) {
        documents.remove(url);
        etags.remove(url);
        lastModified.remove(url);
    }
}
