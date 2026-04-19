package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.language.keywords.KeywordHasher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhraseConstraintGroupListTest {

    private final KeywordHasher hasher = new KeywordHasher.AsciiIsh();

    private TermIdList termIdsFor(String... terms) {
        LongArrayList ids = new LongArrayList();
        for (String t : terms) {
            ids.add(hasher.hashKeyword(t));
        }
        return new TermIdList(ids);
    }

 
    @Test
    public void testBug() {
        // This used to give us an NPE

        TermIdList termIdsAll = termIdsFor("foo", "bar");

        var group = new PhraseConstraintGroupList.PhraseConstraintGroup(
                hasher, List.of("foo", "foo", "bar"), termIdsAll);

        IntList[] positions = new IntList[] {
                new IntArrayList(new int[]{5, 6}),
                new IntArrayList(new int[]{7})
        };

        assertTrue(group.test(positions));
    }

    @Test
    public void testDuplicateTermsNoMatch() {
        TermIdList termIdsAll = termIdsFor("foo", "bar");

        var group = new PhraseConstraintGroupList.PhraseConstraintGroup(
                hasher, List.of("foo", "foo", "bar"), termIdsAll);

        IntList[] positions = new IntList[] {
                new IntArrayList(new int[]{5}),
                new IntArrayList(new int[]{6})
        };

        assertFalse(group.test(positions));
    }

    @Test
    public void testDuplicatesMatch() {
        TermIdList termIdsAll = termIdsFor("foo", "bar");

        var group = new PhraseConstraintGroupList.PhraseConstraintGroup(
                hasher, List.of("foo", "bar"), termIdsAll);

        IntList[] positions = new IntList[] {
                new IntArrayList(new int[]{5}),
                new IntArrayList(new int[]{6})
        };

        assertTrue(group.test(positions));
    }

}
