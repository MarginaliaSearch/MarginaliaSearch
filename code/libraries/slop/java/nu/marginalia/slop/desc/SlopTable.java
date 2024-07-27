package nu.marginalia.slop.desc;

import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.column.ColumnWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final List<ColumnReader> readerList = new ArrayList<>();
    private final List<ColumnWriter> writerList = new ArrayList<>();

    private final Map<String, SlopTable> columnGroups = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(SlopTable.class);

    /** Create a SlopTable corresponding to a grouping of  columns that have their own
     * internal consistency check.  This is needed e.g. for grouped values.  The table is
     * closed automatically by the current instance.
     */
    public SlopTable columnGroup(String name) {
        return columnGroups.computeIfAbsent(name, k -> new SlopTable());
    }

    /** Register a column reader with this table.  This is called from ColumnDesc. */
    void register(ColumnReader reader) {
        readerList.add(reader);
    }

    /** Register a column reader with this table.  This is called from ColumnDesc. */
    void register(ColumnWriter writer) {
        writerList.add(writer);
    }

    public void close() throws IOException {

        Set<Long> positions = new HashSet<>();

        for (ColumnReader reader : readerList) {
            positions.add(reader.position());
            reader.close();
        }
        for (ColumnWriter writer : writerList) {
            positions.add(writer.position());
            writer.close();
        }


        // Check for the scenario where we have multiple positions
        // and one of the positions is zero, indicating that we haven't
        // read or written to one of the columns.  This is likely a bug,
        // but not necessarily a severe one, so we just log a warning.

        if (positions.remove(0L) && !positions.isEmpty()) {
            logger.warn("Zero position found in one of the tables, this is likely development debris");
        }

        // If there are more than one position and several are non-zero, then we haven't maintained the
        // position correctly between the columns.  This is a disaster, so we throw an exception.
        if (positions.size() > 1) {
            throw new IllegalStateException("Expected only one reader position, was " + positions);
        }

        for (var table : columnGroups.values()) {
            table.close();
        }

    }

}
