package nu.marginalia.wmsa.edge.index.service.index.wordstable;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;

import java.util.function.LongConsumer;

import static nu.marginalia.wmsa.edge.index.service.index.wordstable.WordsTableWriter.wordsBTreeContext;

public class BtreeWordsTable extends IndexWordsTable{
    private final MultimapFileLong words;
    private final BTreeReader reader;
    private final BTreeHeader header;
    private final int HEADER_OFFSET = 1;

    public BtreeWordsTable(MultimapFileLong words) {
        this.words = words;


        reader = new BTreeReader(words, wordsBTreeContext);
        header = reader.getHeader(HEADER_OFFSET);

        madvise();
    }

    private void madvise() {
        words.advice(NativeIO.Advice.Random);
        words.advice0(NativeIO.Advice.WillNeed);

        var h = reader.getHeader(HEADER_OFFSET);
        int length = (int)(h.dataOffsetLongs() - h.indexOffsetLongs());
        words.adviceRange(NativeIO.Advice.WillNeed, h.indexOffsetLongs(), length);
        words.pokeRange(h.indexOffsetLongs(), length);
    }

    public void forEachWordsOffset(LongConsumer offsetConsumer) {
        int n = header.numEntries();
        long offset = header.dataOffsetLongs();

        for (int i = 0; i < n; i++) {
            try {
                long posOffset = 2*(offset + i);
                if (posOffset * 8 >= words.size()) {
                    break;
                }

                long sz = words.get(posOffset);
                if ((sz>> 32) > 0) {
                    offsetConsumer.accept(words.get(posOffset+1));
                }
            }
            catch (RuntimeException ex) {
                logger.warn("Error @ " + i, ex);
                break;
            }
        }
    }

    @Override
    public long positionForWord(int wordId) {

        long offset = reader.offsetForEntry(header, wordId);
        if (offset < 0) {
            return -1L;
        }

        return words.get(offset+1);
    }

    @Override
    public int wordLength(int wordId) {

        long offset = reader.offsetForEntry(header, wordId);
        if (offset < 0) {
            return -1;
        }

        return (int)(words.get(offset) >> 32);
    }

    @Override
    public void close() throws Exception {
        words.close();
    }

}
