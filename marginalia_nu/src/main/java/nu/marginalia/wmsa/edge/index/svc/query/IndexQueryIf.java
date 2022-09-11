package nu.marginalia.wmsa.edge.index.svc.query;

import java.util.stream.LongStream;

public interface IndexQueryIf {
    IndexQueryIf EMPTY = new IndexQueryIf() {
        @Override
        public IndexQueryIf also(int wordId) { return this; }

        @Override
        public IndexQueryIf alsoCached(int wordId) { return this; }

        @Override
        public IndexQueryIf not(int wordId) { return this; }

        @Override
        public LongStream stream() { return LongStream.empty(); }
    };

    IndexQueryIf also(int wordId);
    IndexQueryIf alsoCached(int wordId);

    IndexQueryIf not(int wordId);

    LongStream stream();
}
