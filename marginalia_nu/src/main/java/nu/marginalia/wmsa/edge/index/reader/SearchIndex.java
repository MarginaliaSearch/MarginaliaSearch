package nu.marginalia.wmsa.edge.index.reader;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.upserve.uppend.blobs.NativeIO;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class SearchIndex implements  AutoCloseable {

    private final MultimapFileLong urls;
    private final IndexWordsTable words;
    public final String name;
    private final RandomAccessFile wordsFile;

    private final Logger logger;

    @Inject
    public SearchIndex(
            String name,
            @Named("edge-index-read-urls-file") File inUrls,
            @Named("edge-index-read-words-file") File inWords)
            throws IOException {

        logger = LoggerFactory.getLogger(name);
        this.name = name;
        wordsFile = new RandomAccessFile(inWords, "r");

        logger.info("{} : Loading {}", name, inUrls);
        logger.info("{} : Loading {}", name, inWords);

        urls = MultimapFileLong.forReading(inUrls.toPath());
        words = IndexWordsTable.ofFile(wordsFile);

        Schedulers.io().scheduleDirect(() -> madvise(urls));
    }

    private void madvise(MultimapFileLong urls) {

        words.forEachWordsOffset(offset -> {
            var h = BTreeReader.createHeader(urls, offset);
            long length = h.dataOffsetLongs() - h.indexOffsetLongs();

            urls.adviceRange(NativeIO.Advice.WillNeed, offset, 512);

            if (length > 0) {
                urls.adviceRange(NativeIO.Advice.WillNeed, h.indexOffsetLongs(), length);
            }
        });
    }


    public long numUrls(int wordId) {
        int length = words.wordLength(wordId);
        if (length < 0) return 0;
        if (length > 0) return length;

        return rangeForWord(wordId).numEntries();
    }

    public SearchIndexURLRange rangeForWord(int wordId) {
        return new SearchIndexURLRange(urls, words.positionForWord(wordId));
    }

    @Override
    public void close() throws Exception {
        urls.close();
        words.close();

        wordsFile.close();
    }
}
