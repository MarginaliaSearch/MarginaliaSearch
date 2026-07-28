package nu.marginalia.converting.processor.logic.links;

import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileLinksTest {

    private Set<String> keywordsFor(String... urls) throws URISyntaxException {
        Set<EdgeUrl> parsed = new HashSet<>();
        for (String url : urls) {
            parsed.add(new EdgeUrl(url));
        }
        return FileLinks.createFileEndingKeywords(parsed);
    }

    @Test
    void testFileEndingAndCategoryKeywords() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/files/song.mp3");

        assertTrue(keywords.contains("file:mp3"));
        assertTrue(keywords.contains("file:audio"));
    }

    @Test
    void testUppercasePathIsNormalized() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/SONG.MP3");

        assertTrue(keywords.contains("file:mp3"));
        assertTrue(keywords.contains("file:audio"));
    }

    @Test
    void testMultipleCategories() throws URISyntaxException {
        var keywords = keywordsFor(
                "https://example.com/report.pdf",
                "https://example.com/clip.mp4",
                "https://example.com/backup.zip");

        assertTrue(keywords.contains("file:pdf"));
        assertTrue(keywords.contains("file:document"));
        assertTrue(keywords.contains("file:mp4"));
        assertTrue(keywords.contains("file:video"));
        assertTrue(keywords.contains("file:zip"));
        assertTrue(keywords.contains("file:archive"));
    }

    @Test
    void testMultiDotFilenameUsesLastEndingGz() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/dist/release-1.2.tar.gz");

        assertTrue(keywords.contains("file:gz"));
        assertTrue(keywords.contains("file:tar.gz"));
        assertTrue(keywords.contains("file:archive"));
        assertFalse(keywords.contains("file:2.tar.gz"));
    }

    @Test
    void testMultiDotFilenameUsesLastEndingBz2() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/dist/release-1.2.tar.bz2");

        assertTrue(keywords.contains("file:bz2"));
        assertTrue(keywords.contains("file:tar.bz2"));
        assertTrue(keywords.contains("file:archive"));
        assertFalse(keywords.contains("file:2.tar.bz2"));
    }

    @Test
    void testIgnoredEndingsProduceNoKeywords() throws URISyntaxException {
        var keywords = keywordsFor(
                "https://example.com/index.html",
                "https://example.com/page.php",
                "https://example.com/feed.xml");

        assertTrue(keywords.isEmpty());
    }

    @Test
    void testPathWithoutFileEnding() throws URISyntaxException {
        var keywords = keywordsFor(
                "https://example.com/",
                "https://example.com/about",
                "https://example.com/blog/2024/some-post");

        assertTrue(keywords.isEmpty());
    }

    @Test
    void testDotInDirectoryButNotInFilename() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/~user/v1.2/download");

        assertTrue(keywords.isEmpty());
    }

    @Test
    void testQueryParametersAreNotMistakenForEndings() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/download.php?path=music/song.mp3");

        assertFalse(keywords.contains("file:mp3"));
        assertFalse(keywords.contains("file:audio"));
    }

    @Test
    void testUnderscoreEndingIsSkipped() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/page.some_thing");

        assertTrue(keywords.isEmpty());
    }

    @Test
    void testTrailingDotDoesNotProduceEmptyKeyword() throws URISyntaxException {
        var keywords = keywordsFor("https://example.com/weird./");

        assertFalse(keywords.contains("file:"));
    }
}
