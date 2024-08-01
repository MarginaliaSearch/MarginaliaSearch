package nu.marginalia.slop.desc;

import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Describes a slop column.  A column is a named, typed, and paginated sequence of values.
 *
 * @param name the name of the column, must not contain dots
 * @param page the page number of the column, 0 for the first page
 * @param function the function of the column, {@link ColumnFunction}
 * @param type the type of the column, {@link ColumnType}
 * @param storageType the storage type of the column, {@link StorageType}
 * @param <R> the reader type
 * @param <W> the writer type
 */
public record ColumnDesc<R extends ColumnReader,
        W extends ColumnWriter>(
                String name,
                int page,
                ColumnFunction function,
                ColumnType<R, W> type,
                StorageType storageType) {

    public ColumnDesc {
        if (name.contains(".")) {
            throw new IllegalArgumentException("Invalid column name: " + name);
        }
    }

    public ColumnDesc(String name, ColumnType<R, W> type, StorageType storageType) {
        this(name, 0, ColumnFunction.DATA, type, storageType);
    }

    /** Open a column reader for this column.
     *
     * @param table the table to register the reader with
     * @param path the path to the file to read from
     * */
    public R open(SlopTable table, Path path) throws IOException {
        var reader = type.open(path, this);
        table.register(reader);
        return reader;
    }

    /** Create a new column writer for this column.
     *
     * @param table the table to register the writer with
     * @param path the path to the file to write to
     * */
    public W create(SlopTable table, Path path) throws IOException {
        var writer = type.create(path, this);
        table.register(writer);
        return writer;
    }

    public W createUnregistered(Path path) throws IOException {
        return type.create(path, this);
    }

    public R openUnregistered(Path path) throws IOException {
        return type.open(path, this);
    }

    public <R2 extends ColumnReader, W2 extends ColumnWriter >
    ColumnDesc<R2, W2> createSupplementaryColumn(
            ColumnFunction function,
            ColumnType<R2, W2> type,
            StorageType storageType)
    {
        return new ColumnDesc<>(name, page, function, type, storageType);
    }

    public ByteOrder byteOrder() {
        return type.byteOrder();
    }

    public ColumnDesc<R, W> forPage(int page) {
        return new ColumnDesc<>(name, page, function, type, storageType);
    }

    public boolean exists(Path base) {
        return Files.exists(base.resolve(toString()));
    }

    public static ColumnDesc parse(String name) {
        String[] parts = name.split("\\.");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid column name: " + name);
        }

        return new ColumnDesc(parts[0],
                Integer.parseInt(parts[1]),
                ColumnFunction.fromString(parts[2]),
                ColumnType.byMnemonic(parts[3]),
                StorageType.fromString(parts[4])
        );
    }

    @Override
    public String toString() {
        return name + "." + page + "." +  function.nmnemonic + "." + type.mnemonic() + "." + storageType.nmnemonic;
    }

}
