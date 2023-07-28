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
import java.util.function.Consumer;

public class DomainMetadataCompiler {


    public void compile(Consumer<Instruction> instructionConsumer, EdgeDomain domain, @NotNull List<ProcessedDocument> documents) {

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

        instructionConsumer.accept(new LoadDomainMetadata(domain, knownUrls.size(), goodUrls, visitedUrls));
    }

    public void compileFake(Consumer<Instruction> instructionConsumer, EdgeDomain domain, int countAll, int countGood) {
        instructionConsumer.accept(new LoadDomainMetadata(domain, countAll, countGood, countAll));
    }

}
