package nu.marginalia.keyword;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DocumentKeywordExtractorTest {

    @Test
    public void testWordPattern() {
        DocumentKeywordExtractor extractor = new DocumentKeywordExtractor(null);

        Assertions.assertTrue(extractor.matchesWordPattern("test"));
        Assertions.assertTrue(extractor.matchesWordPattern("1234567890abcde"));
        Assertions.assertFalse(extractor.matchesWordPattern("1234567890abcdef"));

        Assertions.assertTrue(extractor.matchesWordPattern("test-test-test-test-test"));
        Assertions.assertFalse(extractor.matchesWordPattern("test-test-test-test-test-test"));
        Assertions.assertTrue(extractor.matchesWordPattern("192.168.1.100/24"));
        Assertions.assertTrue(extractor.matchesWordPattern("std::vector"));
        Assertions.assertTrue(extractor.matchesWordPattern("c++"));
        Assertions.assertTrue(extractor.matchesWordPattern("m*a*s*h"));
        Assertions.assertFalse(extractor.matchesWordPattern("Stulpnagelstrasse"));
    }
}