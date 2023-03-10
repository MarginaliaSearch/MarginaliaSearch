package nu.marginalia.index.svc.searchset;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingSearchSetTest {

    @Test
    public void testSerDes() throws IOException {
        Path p = Files.createTempFile(getClass().getSimpleName(), ".dat");

        var bm = new IntOpenHashSet();
        bm.add(1);
        bm.add(5);
        bm.add(7);
        bm.add(9);

        RankingSearchSet set = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, p, bm);
        set.write();

        RankingSearchSet set2 = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, p);
        assertTrue(set2.contains(1));
        assertTrue(set2.contains(5));
        assertTrue(set2.contains(7));
        assertTrue(set2.contains(9));

        Files.delete(p);

    }

}