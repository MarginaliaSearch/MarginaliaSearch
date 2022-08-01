package nu.marginalia.wmsa.edge.index.reader.query;

import java.util.stream.LongStream;

public interface Query {
    Query EMPTY = new Query() {
        @Override
        public Query also(int wordId) { return this; }

        @Override
        public Query not(int wordId) { return this; }

        @Override
        public LongStream stream() { return LongStream.empty(); }
    };

    Query also(int wordId);
    Query not(int wordId);

    LongStream stream();
}
