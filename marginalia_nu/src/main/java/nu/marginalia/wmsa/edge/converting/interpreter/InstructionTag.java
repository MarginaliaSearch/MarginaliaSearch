package nu.marginalia.wmsa.edge.converting.interpreter;

import nu.marginalia.wmsa.edge.converting.interpreter.instruction.*;

public enum InstructionTag {

    DOMAIN(LoadDomain.class),
    URL(LoadUrl.class),
    LINK(LoadDomainLink.class),
    REDIRECT(LoadDomainRedirect.class),
    WORDS(LoadKeywords.class),
    PROC_DOCUMENT(LoadProcessedDocument.class),
    PROC_DOCUMENT_ERR(LoadProcessedDocumentWithError.class),
    PROC_DOMAIN(LoadProcessedDomain.class),
    RSS(LoadRssFeed.class);

    public final Class<? extends Instruction> clazz;

    InstructionTag(Class<? extends Instruction> clazz) {
        this.clazz = clazz;
    }

}
