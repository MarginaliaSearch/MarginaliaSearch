package nu.marginalia.converting.compiler;

import com.google.inject.Inject;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDomain;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNullElse;

public class InstructionsCompiler {
    private final DocumentsCompiler documentsCompiler;
    private final DomainMetadataCompiler domainMetadataCompiler;
    private final FeedsCompiler feedsCompiler;
    private final LinksCompiler linksCompiler;
    private final RedirectCompiler redirectCompiler;

    private final Logger logger = LoggerFactory.getLogger(InstructionsCompiler.class);

    @Inject
    public InstructionsCompiler(DocumentsCompiler documentsCompiler,
                                DomainMetadataCompiler domainMetadataCompiler,
                                FeedsCompiler feedsCompiler,
                                LinksCompiler linksCompiler,
                                RedirectCompiler redirectCompiler)
    {
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

            int ordinal = 0;
            for (var doc : domain.documents) {
                documentsCompiler.compileDocumentDetails(instructionConsumer, doc, ordinal);
                documentsCompiler.compileWords(instructionConsumer, doc, ordinal);
                ordinal++;
            }

            feedsCompiler.compile(instructionConsumer, domain.documents);
            linksCompiler.compile(instructionConsumer, domain.domain, domain.documents);
        }
        if (domain.redirect != null) {
            redirectCompiler.compile(instructionConsumer, domain.domain, domain.redirect);
        }

        domainMetadataCompiler.compile(instructionConsumer, domain.domain, requireNonNullElse(domain.documents, Collections.emptyList()));
    }

    public void compileStreaming(SideloadSource sideloadSource,
                                 Consumer<Instruction> instructionConsumer) {
        ProcessedDomain domain = sideloadSource.getDomain();
        Iterator<ProcessedDocument> documentsIterator = sideloadSource.getDocumentsStream();

        // Guaranteed to always be first
        instructionConsumer.accept(new LoadProcessedDomain(domain.domain, domain.state, domain.ip));

        int countAll = 0;
        int countGood = 0;

        logger.info("Writing docs");

        while (documentsIterator.hasNext()) {
            var doc = documentsIterator.next();
            countAll++;
            if (doc.isOk()) countGood++;

            documentsCompiler.compileDocumentDetails(instructionConsumer, doc, countAll);
            documentsCompiler.compileWords(instructionConsumer, doc, countAll);
        }

        domainMetadataCompiler.compileFake(instructionConsumer, domain.domain, countAll, countGood);
    }
}
