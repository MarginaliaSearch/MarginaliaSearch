# Slop

Slop is a library for columnar data persistence.  It is designed to be used for storing large amounts of data in a way 
that is both fast and memory-efficient.  The data is write-once, and the slop library offers many facilities for 
deciding how it should be stored and accessed.

Slop is designed as a low abstraction what-you-see-is-what-you-do library, the reason for
this is to be able to eliminate copies and other overheads that are common in higher 
level libraries.  The intent is to get the performance of a hand-rolled solution, but 
without the complexity and brittleness that comes with hand-rolling an ad-hoc row-based storage
format.

A lot of what would commonly be kept in a schema description is instead just 
implemented as code. To aid with portability, slop stores schema information 
in the file names of the data files, besides the actual name of the column itself.   

A table of demographic information may end up stored in files like this:

```text
cities.0.dat.s8[].gz
cities.0.dat-len.varint-le.bin
population.0.dat.s32le.bin
average-age.0.dat.f64le.gz
```

The slop library offers some facilities to aid with data integrity, such as the SlopTable
class, which is a wrapper that ensures consistent positions for a group of columns, and aids 
in closing the columns when they are no longer needed.

## Why though?

Slop is fast.  

Depending on compression and encoding choices, it's possible
to get read speeds that are 5-20x faster than reading from a sqlite database.
When compression is disabled, Slop will memory map the data, and depending on the 
contents of the column, it's possible to perform zero copy reads.

Slop is compact.

Depending on compression and encoding choices, the format will be smaller
than a parquet file containing the equivalent information.

Slop is simple.

There isn't much magic going on under the hood in Slop.  It's designed with the philosophy that a competent programmer
should be able to reverse engineer the format of the data by just
looking at a directory listing of the data files.


### Relaxed 1BRC (no CSV ingestion time)

Slop is reasonably competitive with DuckDB in terms of read speed,
especially when reading from Parquet, and the data on disk tends 
to be smaller.

This is noteworthy given Slop is a single-threaded JVM application,
and DuckDB is a multi-threaded C++ application.

| Impl                       | Runtime | Size On Disk |
|----------------------------|---------|--------------|
| DuckDB in memory           | 2.6s    | 3.0 GB       |
| Slop in vanilla Java s16   | 4.2s    | 2.8 GB       |
| Slop in vanilla Java s32   | 4.5s    | 3.8 GB       |
| Parquet (Snappy) in DuckDB | 4.5s    | 5.5 GB       |
| Parquet (Zstd) in DuckDB   | 5.5s    | 3.0 GB       |

## Example

With slop it's desirable to keep the schema information in the code.  This is an example of how you might use slop to
store a table of data with three columns: source, dest, and counts.  The source and dest columns are strings, and the
counts column is an integer that's stored wit a varint-coding (i.e. like how utf-8 works).  

The data is stored in a directory, and the data is written and read using the `MyData.Writer` and `MyData.Reader` classes.  
The `MyData` class is itself is a record, and the schema is stored as static fields in the `MyData` class. 


```java
record Population(String city, int population, double avgAge) {

    private static final ColumnDesc<StringColumnReader, StringColumnWriter> citiesColumn =
            new ColumnDesc<>("cities", ColumnType.STRING, StorageType.GZIP);
    private static final ColumnDesc<IntColumnReader, IntColumnWriter> populationColumn =
            new ColumnDesc<>("population", ColumnType.INT_LE, StorageType.PLAIN);
    private static final ColumnDesc<DoubleColumnReader, DoubleColumnWriter> averageAgeColumnn =
            new ColumnDesc<>("average-age", ColumnType.DOUBLE_LE, StorageType.PLAIN);

    public static class Writer extends SlopTable {
        private final StringColumnWriter citiesWriter;
        private final IntColumnWriter populationWriter;
        private final DoubleColumnWriter avgAgeWriter;

        public Writer(Path baseDir) throws IOException {
            citiesWriter = citiesColumn.create(this, baseDir);
            populationWriter = populationColumn.create(this, baseDir);
            avgAgeWriter = averageAgeColumnn.create(this, baseDir);
        }

        public void write(Population data) throws IOException {
            citiesWriter.put(data.city);
            populationWriter.put(data.population);
            avgAgeWriter.put(data.avgAge);
        }
    }

    public static class Reader extends SlopTable {
        private final StringColumnReader citiesReader;
        private final IntColumnReader populationReader;
        private final DoubleColumnReader avgAgeReader;

        public Reader(Path baseDir) throws IOException {
            citiesReader = citiesColumn.open(this, baseDir);
            populationReader = populationColumn.open(this, baseDir);
            avgAgeReader = averageAgeColumnn.open(this, baseDir);
        }

        public boolean hasRemaining() throws IOException {
            return citiesReader.hasRemaining();
        }

        public Population read() throws IOException {
            return new Population(
                    citiesReader.get(),
                    populationReader.get(),
                    avgAgeReader.get()
            );
        }
    }
}
```

## Nested Records

TBW

## Column Types

TBW

## Storage Types

TBW

## Extension

TBW