package nu.marginalia.index;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.array.LongArray;
import nu.marginalia.btree.BTreeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class ReverseIndexSelfTest {
    private static final Logger logger = LoggerFactory.getLogger(ReverseIndexSelfTest.class);
    public static void runSelfTest1(LongArray wordsDataRange, long wordsDataSize) {
        logger.info("Starting test 1");

        if (!wordsDataRange.isSortedN(2, 0, wordsDataSize))
            logger.error("Failed test 1: Words data is not sorted");
        else
            logger.info("Passed test 1");
    }

    public static void runSelfTest2(LongArray wordsDataRange, LongArray documents) {
        logger.info("Starting test 2");
        for (long i = 1; i < wordsDataRange.size(); i+=2) {
            var docsBTreeReader = new BTreeReader(documents, ReverseIndexParameters.docsBTreeContext, wordsDataRange.get(i));
            var header = docsBTreeReader.getHeader();
            var docRange = documents.range(header.dataOffsetLongs(), header.dataOffsetLongs() + header.numEntries() * 2L);

            if (!docRange.isSortedN(2, 0, header.numEntries() * 2L)) {
                logger.error("Failed test 2: numEntries={}, offset={}", header.numEntries(), header.dataOffsetLongs());
                return;
            }
        }

        logger.info("Passed test 2");
    }

    public static void runSelfTest3(LongArray wordsDataRange, BTreeReader reader) {
        logger.info("Starting test 3");
        for (long i = 0; i < wordsDataRange.size(); i+=2) {
            if (reader.findEntry(wordsDataRange.get(i)) < 0) {
                logger.error("Failed Test 3");
                return;
            }
        }
        logger.info("Passed test 3");
    }

    public static void runSelfTest4(LongArray wordsDataRange, LongArray documents) {
        logger.info("Starting test 4");
        for (long i = 1; i < wordsDataRange.size(); i+=2) {
            var docsBTreeReader = new BTreeReader(documents, ReverseIndexParameters.docsBTreeContext, wordsDataRange.get(i));
            var header = docsBTreeReader.getHeader();
            var docRange = documents.range(header.dataOffsetLongs(), header.dataOffsetLongs() + header.numEntries() * 2L);
            for (int j = 0; j < docRange.size(); j+=2) {
                if (docsBTreeReader.findEntry(docRange.get(j)) < 0) {
                    logger.info("Failed test 4");
                    return;
                }
            }
        }
        logger.info("Passed test 4");
    }
    public static void runSelfTest5(LongArray wordsDataRange, BTreeReader wordsBTreeReader) {
        logger.info("Starting test 5");
        LongOpenHashSet words = new LongOpenHashSet((int)wordsDataRange.size()/2);
        for (int i = 0; i < wordsDataRange.size(); i+=2) {
            words.add(wordsDataRange.get(i));
        }
        var random = new Random();
        for (int i = 0; i < 100_000_000; i++) {
            long v;
            do {
                v = random.nextLong();
            } while (words.contains(v));
            if (wordsBTreeReader.findEntry(v) >= 0) {
                logger.error("Failed test 5 @ W{}", v);
                return;
            }
        }
        logger.info("Passed test 5");
    }

    public static void runSelfTest6(LongArray wordsDataRange, LongArray documents) {
        logger.info("Starting test 6");
        for (long i = 1; i < wordsDataRange.size(); i+=2) {
            var docsBTreeReader = new BTreeReader(documents, ReverseIndexParameters.docsBTreeContext, wordsDataRange.get(i));
            var header = docsBTreeReader.getHeader();
            var docRange = documents.range(header.dataOffsetLongs(), header.dataOffsetLongs() + header.numEntries() * 2L);
            Long prev = null;
            for (int j = 0; j < docRange.size(); j+=2) {
                if (prev == null) {
                    prev = docRange.get(j);
                    continue;
                }
                long thisVal = prev + 1;
                long nextVal = docRange.get(j);
                while (thisVal < nextVal) {
                    if (docsBTreeReader.findEntry(thisVal) >= 0) {
                        logger.info("Failed test 6 @ W{}:D{}", wordsDataRange.get(i-1), thisVal);
                        return;
                    }
                    thisVal++;
                }
            }
        }
        logger.info("Passed test 6");
    }
}
