package nu.marginalia.converting.compiler;

import com.google.inject.Inject;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDomain;
import nu.marginalia.converting.model.ProcessedDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    public List<Instruction> compile(ProcessedDomain domain) {
        List<Instruction> ret = new ArrayList<>(domain.size()*4);

        ret.add(new LoadProcessedDomain(domain.domain, domain.state, domain.ip));

        if (domain.documents != null) {
            urlsCompiler.compile(ret, domain.documents);
            documentsCompiler.compile(ret, domain.documents);

            feedsCompiler.compile(ret, domain.documents);

            linksCompiler.compile(ret, domain.domain, domain.documents);
        }
        if (domain.redirect != null) {
            redirectCompiler.compile(ret, domain.domain, domain.redirect);
        }

        domainMetadataCompiler.compile(ret, domain.domain, requireNonNullElse(domain.documents, Collections.emptyList()));

        return ret;
    }






}
