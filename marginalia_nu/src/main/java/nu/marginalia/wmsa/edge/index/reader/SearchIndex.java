package nu.marginalia.wmsa.edge.index.reader;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.upserve.uppend.blobs.NativeIO;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.util.btree.CachingBTreeReader;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.stream.LongStream;

public class SearchIndex implements  AutoCloseable {

    private final MultimapFileLong urls;
    private final IndexWordsTable words;
    private final RandomAccessFile wordsFile;
    private final BTreeReader bTreeReader;
    private final CachingBTreeReader cachingBTreeReader;

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
        cachingBTreeReader = new CachingBTreeReader(urls, SearchIndexConverter.urlsBTreeContext);

        Schedulers.io().scheduleDirect(() -> madvise(urls, bTreeReader));
    }

    private void madvise(MultimapFileLong urls, BTreeReader reader) {

        words.forEachWordsOffset(offset -> {
            var h = reader.getHeader(offset);
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

    public UrlIndexTree rangeForWord(int wordId) {
        return new UrlIndexTree(words.positionForWord(wordId));
    }

    public class UrlIndexTree {
        final long dataOffset;
        private BTreeHeader header;
        public UrlIndexTree(long dataOffset) {
            this.dataOffset = dataOffset;
        }

        public LongStream stream() {
            if (dataOffset < 0) {
                return LongStream.empty();
            }
            if (header == null) {
                header = bTreeReader.getHeader(dataOffset);
            }

            long urlOffset = header.dataOffsetLongs();
            long endOffset = header.dataOffsetLongs() + header.numEntries();
            int stepSize = Math.min(1024, header.numEntries());

            long[] buffer = new long[stepSize];

            return LongStream
                    .iterate(urlOffset, i -> i< endOffset, i->i+stepSize)
                    .flatMap(pos -> {
                        int sz = (int)(Math.min(pos+stepSize, endOffset) - pos);
                        urls.read(buffer, sz, pos);
                        return Arrays.stream(buffer, 0, sz);
                    });
        }

        public boolean isPresent() {
            return dataOffset >= 0;
        }

        public long numEntries() {
            if (header != null) {
                return header.numEntries();
            }
            else if (dataOffset < 0) return 0L;
            else {
                header = bTreeReader.getHeader(dataOffset);
                return header.numEntries();
            }
        }

        public boolean hasUrl(long url) {
            if (header != null) {
                return bTreeReader.findEntry(header, url) >= 0;
            }
            else if (dataOffset < 0) return false;
            else {
                header = bTreeReader.getHeader(dataOffset);
                return bTreeReader.findEntry(header, url) >= 0;
            }
        }

        public boolean hasUrl(CachingBTreeReader.Cache cache, long url) {
            if (header != null) {
                return cachingBTreeReader.findEntry(header, cache, url) >= 0;
            }
            else if (dataOffset < 0) return false;
            else {
                header = bTreeReader.getHeader(dataOffset);
                return cachingBTreeReader.findEntry(header, cache, url) >= 0;
            }
        }

        public CachingBTreeReader.Cache createIndexCache() {
            return cachingBTreeReader.prepareCache();
        }
    }



    @Override
    public void close() throws Exception {
        urls.close();
        words.close();

        wordsFile.close();
    }
}
