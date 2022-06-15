package nu.marginalia.wmsa.edge.converting.processor;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.*;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDomain;
import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.*;

public class InstructionsCompiler {

    public List<Instruction> compile(ProcessedDomain domain) {
        List<Instruction> ret = new ArrayList<>(domain.size()*4);

        ret.add(new LoadProcessedDomain(domain.domain, domain.state, domain.ip));

        if (domain.documents != null) {
            compileUrls(ret, domain.documents);
            compileDocuments(ret, domain.documents);
            compileFeeds(ret, domain.documents);

            compileLinks(ret, domain.domain, domain.documents);
        }
        if (domain.redirect != null) {
            compileRedirect(ret, domain.domain, domain.redirect);

        }

        return ret;
    }

    private void compileRedirect(List<Instruction> ret, EdgeDomain from, EdgeDomain to) {
        ret.add(new LoadDomain(to));
        ret.add(new LoadDomainLink(new DomainLink(from, to)));
        ret.add(new LoadDomainRedirect(new DomainLink(from, to)));
    }

    private void compileUrls(List<Instruction> ret, List<ProcessedDocument> documents) {
        Set<EdgeUrl> seenUrls = new HashSet<>(documents.size()*4);
        Set<EdgeDomain> seenDomains = new HashSet<>(documents.size());

        documents.stream().map(doc -> doc.url).forEach(seenUrls::add);

        for (var doc : documents) {
            if (doc.details == null) continue;
            for (var url : doc.details.linksExternal) {
                seenDomains.add(url.domain);
            }
            seenUrls.addAll(doc.details.linksExternal);
            seenUrls.addAll(doc.details.linksInternal);
        }

        ret.add(new LoadDomain(seenDomains.toArray(EdgeDomain[]::new)));
        ret.add(new LoadUrl(seenUrls.toArray(EdgeUrl[]::new)));
    }

    private void compileLinks(List<Instruction> ret, EdgeDomain from, List<ProcessedDocument> documents) {
        DomainLink[] links = documents.stream().map(doc -> doc.details)
                .filter(Objects::nonNull)
                .flatMap(dets -> dets.linksExternal.stream())
                .map(link -> link.domain)
                .distinct()
                .map(domain -> new DomainLink(from, domain))
                .toArray(DomainLink[]::new);

        ret.add(new LoadDomainLink(links));
    }

    private void compileFeeds(List<Instruction> ret, List<ProcessedDocument> documents) {

        EdgeUrl[] feeds = documents.stream().map(doc -> doc.details)
                .filter(Objects::nonNull)
                .flatMap(dets -> dets.feedLinks.stream())
                .distinct()
                .toArray(EdgeUrl[]::new);

        ret.add(new LoadRssFeed(feeds));
    }

    private void compileDocuments(List<Instruction> ret, List<ProcessedDocument> documents) {

        for (var doc : documents) {
            compileDocumentDetails(ret, doc);
        }

        for (var doc : documents) {
            compileWords(ret, doc);
        }

    }

    private void compileDocumentDetails(List<Instruction> ret, ProcessedDocument doc) {
        var details = doc.details;

        if (details != null) {
            ret.add(new LoadProcessedDocument(doc.url, doc.state, details.title, details.description, HtmlFeature.encode(details.features), details.standard, details.length, details.hashCode, details.quality));
        }
        else {
            ret.add(new LoadProcessedDocumentWithError(doc.url, doc.state));
        }
    }

    private void compileWords(List<Instruction> ret, ProcessedDocument doc) {
        var words = doc.words;
        if (words != null) {
            var wordsArray = words.values().stream()
                    .map(DocumentKeywords::new)
                    .toArray(DocumentKeywords[]::new);

            ret.add(new LoadKeywords(doc.url, wordsArray));
        }
    }
}
