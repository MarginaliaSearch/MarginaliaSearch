package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadDomainMetadata;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DomainMetadataCompiler {


    public void compile(List<Instruction> ret, EdgeDomain domain, @NotNull List<ProcessedDocument> documents) {

        int visitedUrls = 0;
        int goodUrls = 0;

        Set<EdgeUrl> knownUrls = new HashSet<>(documents.size() * 2);

        for (var doc : documents) {
            visitedUrls++;

            if (doc.isOk()) {
                goodUrls++;
            }

            knownUrls.add(doc.url);

            Optional.ofNullable(doc.details)
                    .map(details -> details.linksInternal)
                    .ifPresent(knownUrls::addAll);
        }

        ret.add(new LoadDomainMetadata(domain, knownUrls.size(), goodUrls, visitedUrls));
    }

}
