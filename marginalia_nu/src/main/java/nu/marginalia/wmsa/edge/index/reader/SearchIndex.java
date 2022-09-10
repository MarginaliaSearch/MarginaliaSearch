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
import nu.marginalia.wmsa.edge.index.reader.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.reader.query.types.QueryFilterStep;
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
    public final String name;
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
        this.name = name;
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


    public long numUrls(IndexQueryCachePool pool, int wordId) {
        int length = words.wordLength(wordId);
        if (length < 0) return 0;
        if (length > 0) return length;

        return rangeForWord(pool, wordId).numEntries();
    }

    public UrlIndexTree rangeForWord(IndexQueryCachePool pool, int wordId) {
        UrlIndexTree range = pool.getRange(words, wordId);

        if (range == null) {
            range = new UrlIndexTree(words.positionForWord(wordId));
            pool.cacheRange(words, wordId, range);
        }

        return range;
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

        public LongStream stream(int bufferSize) {
            if (dataOffset < 0) {
                return LongStream.empty();
            }
            if (header == null) {
                header = bTreeReader.getHeader(dataOffset);
            }

            long urlOffset = header.dataOffsetLongs();
            long endOffset = header.dataOffsetLongs() + header.numEntries();
            int stepSize = Math.min(bufferSize, header.numEntries());

            long[] buffer = new long[stepSize];

            return LongStream
                    .iterate(urlOffset, i -> i< endOffset, i->i+stepSize)
                    .flatMap(pos -> {
                        int sz = (int)(Math.min(pos+stepSize, endOffset) - pos);
                        urls.read(buffer, sz, pos);
                        return Arrays.stream(buffer, 0, sz);
                    });
        }

        public EntrySource asEntrySource() {
            return new AsEntrySource();
        }

        public QueryFilterStep asExcludeFilterStep(IndexQueryCachePool pool) {
            return new AsExcludeQueryFilterStep(pool);
        }


        public LongStream stream() {
            return stream(1024);
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

        public boolean hasUrl(CachingBTreeReader.Cache cache, long url) {
            if (dataOffset < 0) return false;

            return cachingBTreeReader.findEntry(cache, url) >= 0;
        }

        public boolean hasUrl(IndexQueryCachePool pool, long url) {
            if (dataOffset < 0)
                return false;

            CachingBTreeReader.Cache cache = pool.getIndexCache(SearchIndex.this, this);

            return cachingBTreeReader.findEntry(cache, url) >= 0;
        }

        public CachingBTreeReader.Cache createIndexCache() {
            if (dataOffset < 0)
                return null;

            if (header == null) {
                header = cachingBTreeReader.getHeader(dataOffset);
            }

            return cachingBTreeReader.prepareCache(header);
        }

        class AsEntrySource implements EntrySource {
            long pos;
            final long endOffset;

            public SearchIndex getIndex() {
                return SearchIndex.this;
            };

            public AsEntrySource() {
                if (dataOffset <= 0) {
                    pos = -1;
                    endOffset = -1;
                    return;
                }

                if (header == null) {
                    header = bTreeReader.getHeader(dataOffset);
                }

                pos = header.dataOffsetLongs();
                endOffset = header.dataOffsetLongs() + header.numEntries();
            }


            @Override
            public int read(long[] buffer, int n) {
                if (pos >= endOffset) {
                    return 0;
                }

                int rb = Math.min(n, (int)(endOffset - pos));
                urls.read(buffer, rb, pos);
                pos += rb;
                return rb;
            }
        }

        class AsExcludeQueryFilterStep implements QueryFilterStep {
            private final CachingBTreeReader.Cache cache;

            public AsExcludeQueryFilterStep(IndexQueryCachePool pool) {
                cache = pool.getIndexCache(SearchIndex.this, UrlIndexTree.this);
            }

            public SearchIndex getIndex() {
                return SearchIndex.this;
            };
            public double cost() {
                return cache.getIndexedDataSize();
            }

            @Override
            public boolean test(long value) {
                return !hasUrl(cache, value);
            }

            public String describe() {
                return "Exclude["+name+"]";
            }
        }

    }

    @Override
    public void close() throws Exception {
        urls.close();
        words.close();

        wordsFile.close();
    }
}
