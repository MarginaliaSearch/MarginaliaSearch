package nu.marginalia.nsfw.document;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OllamaNsfwLabelerTest {

    // --- parseLabel unit tests (no Ollama needed) ---

    @Test
    void parseLabelNsfw() throws IOException {
        String json = """
                {"model":"qwen3:8b","response":"NSFW","done":true}
                """;
        assertEquals("__label__NSFW", OllamaNsfwLabeler.parseLabel(json));
    }

    @Test
    void parseLabelSafe() throws IOException {
        String json = """
                {"model":"qwen3:8b","response":"SAFE","done":true}
                """;
        assertEquals("__label__SAFE", OllamaNsfwLabeler.parseLabel(json));
    }

    @Test
    void parseLabelCaseInsensitive() throws IOException {
        String json = """
                {"model":"qwen3:8b","response":"nsfw","done":true}
                """;
        assertEquals("__label__NSFW", OllamaNsfwLabeler.parseLabel(json));
    }

    @Test
    void parseLabelWithExtraText() throws IOException {
        // Some models might respond with more than just the label
        String json = """
                {"model":"qwen3:8b","response":"NSFW.","done":true}
                """;
        assertEquals("__label__NSFW", OllamaNsfwLabeler.parseLabel(json));
    }

    @Test
    void parseLabelUnrecognizedThrows() {
        String json = """
                {"model":"qwen3:8b","response":"I'm not sure","done":true}
                """;
        assertThrows(IOException.class, () -> OllamaNsfwLabeler.parseLabel(json));
    }

    @Test
    void parseLabelMissingResponseFieldThrows() {
        String json = """
                {"model":"qwen3:8b","done":true}
                """;
        assertThrows(IOException.class, () -> OllamaNsfwLabeler.parseLabel(json));
    }

    @Test
    void parseLabelEmptyStringThrows() {
        assertThrows(IOException.class, () -> OllamaNsfwLabeler.parseLabel(""));
    }

    // --- Integration tests (require running Ollama) ---

    @Test
    @Tag("slow")
    void classifyObviousNsfw() throws IOException, InterruptedException {
        try (OllamaNsfwLabeler labeler = new OllamaNsfwLabeler("localhost", 11434, OllamaNsfwLabeler.DEFAULT_MODEL)) {
            assumeTrue(labeler.isAvailable(), "Ollama is not running or model is missing");

            String label = labeler.classify(
                    "Free XXX Porn Videos",
                    "Watch free adult videos and pornographic content"
            );
            assertEquals("__label__NSFW", label);
        }
    }

    @Test
    @Tag("slow")
    void classifyObviousSafe() throws IOException, InterruptedException {
        try (OllamaNsfwLabeler labeler = new OllamaNsfwLabeler("localhost", 11434, OllamaNsfwLabeler.DEFAULT_MODEL)) {
            assumeTrue(labeler.isAvailable(), "Ollama is not running or model is missing");

            String label = labeler.classify(
                    "Introduction to Linear Algebra - MIT OpenCourseWare",
                    "This course covers matrix theory and linear algebra"
            );
            assertEquals("__label__SAFE", label);
        }
    }
}
