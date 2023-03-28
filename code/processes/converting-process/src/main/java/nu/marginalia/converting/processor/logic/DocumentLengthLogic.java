package nu.marginalia.converting.processor.logic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.idx.DocumentFlags;

import java.util.EnumSet;

@Singleton
public class DocumentLengthLogic {
    private final int minDocumentLength;
    private final int shortDocumentLength = 2500;
    private final int longDocumentLength = 7500;

    @Inject
    public DocumentLengthLogic(@Named("min-document-length") Integer minDocumentLength) {
        this.minDocumentLength = minDocumentLength;
    }

    public void setLengthFlags(int lengthTextInChars, EnumSet<DocumentFlags> flags) {
        if (lengthTextInChars < shortDocumentLength)
            flags.add(DocumentFlags.ShortDocument);
        else if (lengthTextInChars > longDocumentLength)
            flags.add(DocumentFlags.LongDocument);
    }

    public void validateLength(DocumentLanguageData dld) throws DisqualifiedException {
        if (dld.totalNumWords() < minDocumentLength) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LENGTH);
        }
    }

}
