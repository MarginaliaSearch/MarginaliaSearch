package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.apache.commons.lang3.StringUtils;

import java.util.StringJoiner;

public sealed interface UnrankedCursor {

    public String encode();

    public static UnrankedCursor forPositions(IntList nodes, LongList indexes) {
        if (nodes.isEmpty())
            return new Terminal();
        return new Partial(nodes, indexes);
    }

    static UnrankedCursor parse(String cursor) throws NumberFormatException {
        if (StringUtils.isEmpty(cursor))
            return new Uninitialized();

        if ("FIN".equals(cursor))
            return new Terminal();

        IntList nodes = new IntArrayList();
        LongList positions = new LongArrayList();

        String[] parts = StringUtils.split(cursor, '.');

        for (var part: parts) {
            int node = Integer.parseInt(part.substring(0, 1), 36);
            long position = Long.parseUnsignedLong(part.substring(1), 36);

            nodes.add(node);
            positions.add(position);
        }

        return new Partial(nodes, positions);
    }

    static String encodeLong(long value) {
        return Long.toUnsignedString(value, 36);
    }

    record Uninitialized() implements UnrankedCursor {
        public String encode() {
            return "";
        }
    }

    record Terminal() implements UnrankedCursor {
        public String encode() {
            return "FIN";
        }
    }

    record Partial(IntList nodes, LongList positions) implements UnrankedCursor {
        public String encode() {
            StringJoiner joiner = new StringJoiner(".");
            for (int i = 0; i < nodes.size(); i++) {
                joiner.add(Integer.toString(nodes.getInt(i), 36) + Long.toUnsignedString(positions.getLong(i), 36));
            }
            return joiner.toString();
        }
    }
}
