package nu.marginalia.wmsa.edge.converting.compiler;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadDomainLink;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

import java.util.List;
import java.util.Objects;

public class LinksCompiler {

    public void compile(List<Instruction> ret, EdgeDomain from, List<ProcessedDocument> documents) {

        DomainLink[] links = documents.stream().map(doc -> doc.details)
                .filter(Objects::nonNull)
                .flatMap(dets -> dets.linksExternal.stream())
                .map(link -> link.domain)
                .distinct()
                .map(domain -> new DomainLink(from, domain))
                .toArray(DomainLink[]::new);

        ret.add(new LoadDomainLink(links));
    }
}
