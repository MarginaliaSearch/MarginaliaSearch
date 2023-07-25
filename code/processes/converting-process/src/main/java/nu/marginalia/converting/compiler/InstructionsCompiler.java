package nu.marginalia.converting.compiler;

import com.google.inject.Inject;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDomain;
import nu.marginalia.converting.model.ProcessedDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNullElse;

public class InstructionsCompiler {
    private final UrlsCompiler urlsCompiler;
    private final DocumentsCompiler documentsCompiler;
    private final DomainMetadataCompiler domainMetadataCompiler;
    private final FeedsCompiler feedsCompiler;
    private final LinksCompiler linksCompiler;
    private final RedirectCompiler redirectCompiler;

    @Inject
    public InstructionsCompiler(UrlsCompiler urlsCompiler,
                                DocumentsCompiler documentsCompiler,
                                DomainMetadataCompiler domainMetadataCompiler,
                                FeedsCompiler feedsCompiler,
                                LinksCompiler linksCompiler,
                                RedirectCompiler redirectCompiler)
    {
        this.urlsCompiler = urlsCompiler;
        this.documentsCompiler = documentsCompiler;
        this.domainMetadataCompiler = domainMetadataCompiler;
        this.feedsCompiler = feedsCompiler;
        this.linksCompiler = linksCompiler;
        this.redirectCompiler = redirectCompiler;
    }

    public void compile(ProcessedDomain domain, Consumer<Instruction> instructionConsumer) {
        // Guaranteed to always be first
        instructionConsumer.accept(new LoadProcessedDomain(domain.domain, domain.state, domain.ip));

        if (domain.documents != null) {
            urlsCompiler.compile(instructionConsumer, domain.documents);
            documentsCompiler.compile(instructionConsumer, domain.documents);

            feedsCompiler.compile(instructionConsumer, domain.documents);
            linksCompiler.compile(instructionConsumer, domain.domain, domain.documents);
        }
        if (domain.redirect != null) {
            redirectCompiler.compile(instructionConsumer, domain.domain, domain.redirect);
        }

        domainMetadataCompiler.compile(instructionConsumer, domain.domain, requireNonNullElse(domain.documents, Collections.emptyList()));
    }
}
