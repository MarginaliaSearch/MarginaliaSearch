package nu.marginalia.wmsa.edge.converting.compiler;

import com.google.inject.Inject;
import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDomain;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDomain;

import java.util.ArrayList;
import java.util.List;

public class InstructionsCompiler {
    private final UrlsCompiler urlsCompiler;
    private final DocumentsCompiler documentsCompiler;
    private final FeedsCompiler feedsCompiler;
    private final LinksCompiler linksCompiler;
    private final RedirectCompiler redirectCompiler;

    @Inject
    public InstructionsCompiler(UrlsCompiler urlsCompiler,
                                DocumentsCompiler documentsCompiler,
                                FeedsCompiler feedsCompiler,
                                LinksCompiler linksCompiler,
                                RedirectCompiler redirectCompiler)
    {
        this.urlsCompiler = urlsCompiler;
        this.documentsCompiler = documentsCompiler;
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

        return ret;
    }






}
