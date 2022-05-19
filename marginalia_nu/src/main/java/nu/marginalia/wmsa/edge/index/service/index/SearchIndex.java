package nu.marginalia.wmsa.edge.index.service.index;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.wmsa.edge.index.service.index.wordstable.IndexWordsTable;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.stream.LongStream;

public class SearchIndex implements  AutoCloseable {

    private final MultimapFileLong urls;
    private final IndexWordsTable words;
    private final RandomAccessFile wordsFile;
    private final BTreeReader bTreeReader;
    private final Logger logger;

    @Inject
    public SearchIndex(
            String name,
            @Named("edge-index-read-urls-file") File inUrls,
            @Named("edge-index-read-words-file") File inWords)
            throws IOException {

        logger = LoggerFactory.getLogger(name);
        wordsFile = new RandomAccessFile(inWords, "r");

        logger.info("{} : Loading {}", name, inUrls);
        logger.info("{} : Loading {}", name, inWords);

        urls = MultimapFileLong.forReading(inUrls.toPath());
        words = IndexWordsTable.ofFile(wordsFile);

        bTreeReader = new BTreeReader(urls, SearchIndexConverter.urlsBTreeContext);

        madvise(urls, bTreeReader);
    }

    private void madvise(MultimapFileLong urls, BTreeReader reader) {

        urls.advice(NativeIO.Advice.Sequential);
        words.forEachWordsOffset(offset -> {
            var h = reader.getHeader(offset);
            int length = (int) (h.dataOffsetLongs() - h.indexOffsetLongs());

            if (length > 0) {
                urls.adviceRange(NativeIO.Advice.WillNeed, h.indexOffsetLongs(), length);
                urls.pokeRange(h.indexOffsetLongs(), length);
            }
        });
    }


    public long numUrls(int wordId) {
        int length = words.wordLength(wordId);
        if (length < 0) return 0;
        if (length > 0) return length;

        var range = rangeForWord(wordId);
        if (range.isPresent()) {
            return bTreeReader.getHeader(range.dataOffset).numEntries();
        }
        return 0;
    }

    public UrlIndexTree rangeForWord(int wordId) {
        return new UrlIndexTree(words.positionForWord(wordId));
    }

    public boolean hasUrl(long url, UrlIndexTree range) {
        if (!range.isPresent())
            return false;

        return bTreeReader.offsetForEntry(bTreeReader.getHeader(range.dataOffset), url) >= 0;
    }

    public class UrlIndexTree {
        final long dataOffset;

        public UrlIndexTree(long dataOffset) {
            this.dataOffset = dataOffset;
        }

        public LongStream stream() {
            if (dataOffset < 0) {
                return LongStream.empty();
            }
            var header = bTreeReader.getHeader(dataOffset);

            long urlOffset = header.dataOffsetLongs();
            return LongStream.range(urlOffset, urlOffset + header.numEntries()).map(urls::get);
        }

        public boolean isPresent() {
            return dataOffset >= 0;
        }
    }



    @Override
    public void close() throws Exception {
        urls.close();
        words.close();

        wordsFile.close();
    }
}
