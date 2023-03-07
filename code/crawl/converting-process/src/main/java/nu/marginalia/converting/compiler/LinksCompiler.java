package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadDomainLink;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeDomain;

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
