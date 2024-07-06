package nu.marginalia.index;

import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeReader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ReverseIndexDebugTest {
    @Test
    @Disabled // this is a debugging utility
    public void debug() throws IOException {
        long problemWord = -7909917549851025932L;
        long problemDoc = 9079256848846028801L;

        var words = LongArrayFactory.mmapForReadingConfined(Path.of("/home/vlofgren/Code/MarginaliaSearch/run/node-1/index/ir/rev-words.dat"));
        var documents = LongArrayFactory.mmapForReadingConfined(Path.of("/home/vlofgren/Code/MarginaliaSearch/run/node-1/index/ir/rev-docs.dat"));

        var wordsBTreeReader = new BTreeReader(words, ReverseIndexParameters.wordsBTreeContext, 0);
        var wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();

        long wordOffset = wordsBTreeReader.findEntry(problemWord);
        assertTrue(wordOffset >= 0);

        var docsReader = new BTreeReader(documents, ReverseIndexParameters.prioDocsBTreeContext, wordOffset);

        // We find problemDoc even though it doesn't exist in the document range
        long docOffset = docsReader.findEntry(problemDoc);
        assertTrue(docOffset < 0);

        // We know it doesn't exist because when we check, we can't find it,
        // either by iterating...
        var dataRange = docsReader.data();
        System.out.println(dataRange.size());
        for (int i = 0; i < dataRange.size(); i+=2) {

            assertNotEquals(problemDoc, dataRange.get(i));
        }

        // or by binary searching
        assertTrue(dataRange.binarySearchN(2, problemDoc, 0, dataRange.size()) < 0);


    }
}
