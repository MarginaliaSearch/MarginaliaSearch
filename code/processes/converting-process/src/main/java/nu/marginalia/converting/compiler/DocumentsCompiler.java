package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadKeywords;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.crawl.HtmlFeature;

import java.util.List;

public class DocumentsCompiler {

    public void compile(List<Instruction> ret, List<ProcessedDocument> documents) {

        for (var doc : documents) {
            compileDocumentDetails(ret, doc);
        }

        for (var doc : documents) {
            compileWords(ret, doc);
        }

    }

    private void compileDocumentDetails(List<Instruction> ret, ProcessedDocument doc) {
        var details = doc.details;

        if (details != null) {
            ret.add(new LoadProcessedDocument(doc.url, doc.state, details.title, details.description, HtmlFeature.encode(details.features), details.standard.name(), details.length, details.hashCode, details.quality, details.pubYear));
        }
    }

    private void compileWords(List<Instruction> ret, ProcessedDocument doc) {
        var words = doc.words;

        if (words != null) {
            ret.add(new LoadKeywords(doc.url, doc.details.metadata, words.build()));
        }
    }

}
