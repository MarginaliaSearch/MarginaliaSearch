package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadDomain;
import nu.marginalia.converting.instruction.instructions.LoadUrl;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

public class UrlsCompiler {

    private static final int MAX_INTERNAL_LINKS = 25;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void compile(Consumer<Instruction> instructionConsumer, List<ProcessedDocument> documents) {
        Set<EdgeUrl> seenUrls = new HashSet<>(documents.size()*4);
        Set<EdgeDomain> seenDomains = new HashSet<>(documents.size());

        for (var doc : documents) {
            if (doc.url == null) {
                logger.warn("Discovered document with null URL");
                continue;
            }

            seenUrls.add(doc.url);

            if (doc.details == null) {
                continue;
            }

            // Add *some* external links; to avoid loading too many and gunking up the database with nonsense,
            // only permit this once per external domain per crawled domain
            for (var url : doc.details.linksExternal) {
                if (seenDomains.add(url.domain)) {
                    seenUrls.add(url);
                }
            }

            if (doc.isOk()) {
                // Don't load more than a few from linksInternal, grows too big for no reason
                var linksToAdd = new ArrayList<>(doc.details.linksInternal);
                if (linksToAdd.size() > MAX_INTERNAL_LINKS) {
                    linksToAdd.subList(MAX_INTERNAL_LINKS, linksToAdd.size()).clear();
                }
                seenUrls.addAll(linksToAdd);
            }
        }

        instructionConsumer.accept(new LoadDomain(seenDomains.toArray(EdgeDomain[]::new)));
        instructionConsumer.accept(new LoadUrl(seenUrls.toArray(EdgeUrl[]::new)));
    }

    public void compileJustUrls(Consumer<Instruction> instructionConsumer, Iterator<EdgeUrl> urlsIterator) {
        var urls = new ArrayList<EdgeUrl>(1000);

        while (urlsIterator.hasNext()) {
            if (urls.size() >= 1000) {
                instructionConsumer.accept(new LoadUrl(urls.toArray(EdgeUrl[]::new)));
                urls.clear();
            }

            urls.add(urlsIterator.next());
        }
        if (!urls.isEmpty()) {
            instructionConsumer.accept(new LoadUrl(urls.toArray(EdgeUrl[]::new)));
        }
    }

    public void compileJustDomain(Consumer<Instruction> instructionConsumer, EdgeDomain domain) {
        instructionConsumer.accept(new LoadDomain(domain));
    }
}
