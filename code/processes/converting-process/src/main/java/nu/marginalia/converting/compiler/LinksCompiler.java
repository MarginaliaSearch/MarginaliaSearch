package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadDomain;
import nu.marginalia.converting.instruction.instructions.LoadDomainLink;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeDomain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class LinksCompiler {

    public void compile(Consumer<Instruction> instructionConsumer,
                        EdgeDomain from,
                        List<ProcessedDocument> documents) {

        EdgeDomain[] domains = documents.stream()
                .map(doc -> doc.details)
                .filter(Objects::nonNull)
                .flatMap(details -> details.linksExternal.stream())
                .map(link -> link.domain)
                .distinct()
                .toArray(EdgeDomain[]::new);

        DomainLink[] links = new DomainLink[domains.length];
        Arrays.setAll(links, i -> new DomainLink(from, domains[i]));

        instructionConsumer.accept(new LoadDomain(domains));
        instructionConsumer.accept(new LoadDomainLink(links));
    }
}
