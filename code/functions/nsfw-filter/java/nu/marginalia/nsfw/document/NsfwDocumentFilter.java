package nu.marginalia.nsfw.document;

import com.google.inject.Inject;
import nu.marginalia.language.model.DocumentSentence;

import java.util.List;

public class NsfwDocumentFilter {

    @Inject
    public void NsfwDocumentFilter() {

    }

    public boolean isNsfw(String title, String description) {
        return false; // STUB
    }

    public boolean isNsfw(List<DocumentSentence> sentences) {
        return false; // STUB
    }
}
