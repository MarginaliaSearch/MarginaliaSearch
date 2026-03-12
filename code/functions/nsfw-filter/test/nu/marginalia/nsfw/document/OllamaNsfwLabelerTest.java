package nu.marginalia.nsfw.document;

import org.junit.Assume;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class OllamaNsfwLabelerTest {

    @Test
    void classifyNsfw() throws IOException, InterruptedException {
        try (var labeler = new OllamaNsfwLabeler()) {
            Assume.assumeTrue(labeler.isAvailable());

            Assertions.assertTrue(labeler.classifyNsfw("Big tits", "Big tits hentai nsfw lewd pussy"));
            Assertions.assertFalse(labeler.classifyNsfw("I am a teapot", "Short and stout"));
        }
    }
}