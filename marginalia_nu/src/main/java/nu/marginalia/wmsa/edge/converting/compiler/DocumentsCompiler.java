package nu.marginalia.wmsa.edge.converting.compiler;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadKeywords;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocumentWithError;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.index.model.IndexBlockType;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;

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
            ret.add(new LoadProcessedDocument(doc.url, doc.state, details.title, details.description, HtmlFeature.encode(details.features), details.standard, details.length, details.hashCode, details.quality));
        }
        else {
            ret.add(new LoadProcessedDocumentWithError(doc.url, doc.state, doc.stateReason));
        }
    }

    private void compileWords(List<Instruction> ret, ProcessedDocument doc) {
        var words = doc.words;

        if (words != null) {

            var wordsArray = words.values().stream()
                    .filter(this::filterNonTransients)
                    .map(DocumentKeywords::new)
                    .toArray(DocumentKeywords[]::new);

            ret.add(new LoadKeywords(doc.url, wordsArray));
        }
    }

    private boolean filterNonTransients(EdgePageWords words) {
        return words.block.type != IndexBlockType.TRANSIENT;
    }

}
