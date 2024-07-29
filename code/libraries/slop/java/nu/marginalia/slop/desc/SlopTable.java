package nu.marginalia.slop.desc;

import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.column.ColumnWriter;
import nu.marginalia.slop.column.ObjectColumnReader;

import java.io.IOException;
import java.util.*;

/** SlopTable is a utility class for managing a group of columns that are
 * read and written together.  It is used to ensure that the reader and writer
 * positions are maintained correctly between the columns, and to ensure that
 * the columns are closed correctly.
 * <p></p>
 * To deal with the fact that some columns may not be expected to have the same
 * number of rows, SlopTable supports the concept of column groups.  Each column
 * group is a separate SlopTable instance, and the columns in the group are
 * managed together.
 * <p></p>
 * It is often a good idea to let the reader or writer class for a particular
 * table inherit from SlopTable, so that the table is automatically closed when
 * the reader or writer is closed.
 */

public class SlopTable implements AutoCloseable {
    private final Set<ColumnReader> readerList = new HashSet<>();
    private final Set<ColumnWriter> writerList = new HashSet<>();

    /** Register a column reader with this table.  This is called from ColumnDesc. */
    void register(ColumnReader reader) {
        if (!readerList.add(reader))
            System.err.println("Double registration of " + reader);
    }

    /** Register a column reader with this table.  This is called from ColumnDesc. */
    void register(ColumnWriter writer) {
        if (!writerList.add(writer))
            System.err.println("Double registration of " + writer);
    }

    protected <T> boolean find(ObjectColumnReader<T> column, T value) throws IOException {
        boolean ret = column.search(value);

        long desiredPos = column.position() - 1;

        for (var otherReader : readerList) {
            if (otherReader.position() < desiredPos) {
                otherReader.skip(desiredPos - otherReader.position());
            }
        }

        return ret;
    }

    public void close() throws IOException {

        Map<Long, List<ColumnDesc>> positions = new HashMap<>();

        for (ColumnReader reader : readerList) {
            positions.computeIfAbsent(reader.position(), k -> new ArrayList<>()).add(reader.columnDesc());
            reader.close();
        }
        for (ColumnWriter writer : writerList) {
            positions.computeIfAbsent(writer.position(), k -> new ArrayList<>()).add(writer.columnDesc());
            writer.close();
        }


        // Check for the scenario where we have multiple positions
        // and one of the positions is zero, indicating that we haven't
        // read or written to one of the columns.  This is likely a bug,
        // but not necessarily a severe one, so we just log a warning.

        var zeroPositions = Objects.requireNonNullElseGet(positions.remove(0L), List::of);
        if (!zeroPositions.isEmpty() && !positions.isEmpty()) {
            System.err.println("Zero position found in {}, this is likely development debris" + zeroPositions);
        }

        // If there are more than one position and several are non-zero, then we haven't maintained the
        // position correctly between the columns.  This is a disaster, so we throw an exception.
        if (positions.size() > 1) {
            throw new IllegalStateException("Expected only one reader position, found " + positions);
        }
    }

}
