package nu.marginalia.api.searchquery.model.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchPhraseConstraintTest {

    @Test
    public void testTokenizerDiscardedTokensAreRemoved() {
        assertEquals(List.of("coca", "cola"),
                SearchPhraseConstraint.mandatory("coca", "-", "cola").terms());
        assertEquals(List.of("five", "six"),
                SearchPhraseConstraint.mandatory("five", "*", "six").terms());
    }

    @Test
    public void testJunkWordsBecomePlaceholders() {
        assertEquals(List.of("part", "number", "", "in", "stock"),
                SearchPhraseConstraint.mandatory("part", "number", "123456789012345678", "in", "stock").terms());
        assertEquals(List.of("foo", "", "bar"),
                SearchPhraseConstraint.mandatory("foo", "-foo-", "bar").terms());
    }

    @Test
    public void testLeadingAndTrailingPlaceholdersAreTrimmed() {
        assertEquals(List.of("foo"),
                SearchPhraseConstraint.mandatory("123456789012345678", "foo").terms());
        assertEquals(List.of("foo"),
                SearchPhraseConstraint.mandatory("foo", "123456789012345678").terms());
        assertEquals(List.of(),
                SearchPhraseConstraint.mandatory("123456789012345678", "-").terms());
    }

    @Test
    public void testListOverloadBehavesLikeVarargs() {
        assertEquals(List.of("coca", "cola"),
                SearchPhraseConstraint.optional(List.of("coca", "-", "cola")).terms());
        assertEquals(List.of("foo", "", "bar"),
                SearchPhraseConstraint.full(List.of("foo", "123456789012345678", "bar")).terms());
        assertEquals(List.of("foo"),
                SearchPhraseConstraint.optional(List.of("123456789012345678", "foo")).terms());
    }

    @Test
    public void testPlainWordsPassThrough() {
        assertEquals(List.of("foo", "bar"),
                SearchPhraseConstraint.mandatory("foo", "bar").terms());
    }
}
