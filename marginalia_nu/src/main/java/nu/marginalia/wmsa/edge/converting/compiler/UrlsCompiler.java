package nu.marginalia.wmsa.edge.converting.compiler;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadDomain;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadUrl;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UrlsCompiler {

    private static final int MAX_INTERNAL_LINKS = 25;

    public void compile(List<Instruction> ret, List<ProcessedDocument> documents) {
        Set<EdgeUrl> seenUrls = new HashSet<>(documents.size()*4);
        Set<EdgeDomain> seenDomains = new HashSet<>(documents.size());

        for (var doc : documents) {
            seenUrls.add(doc.url);

            if (doc.details != null) {

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
        }

        ret.add(new LoadDomain(seenDomains.toArray(EdgeDomain[]::new)));
        ret.add(new LoadUrl(seenUrls.toArray(EdgeUrl[]::new)));
    }

}
