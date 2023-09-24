Parquet is used as an intermediate storage format for a lot of processed data.

See [third-party/parquet-floor](../third-party/parquet-floor).

## How to query the data?

[DuckDB](https://duckdb.org/) is probably the best tool for interacting with these files.  You can
query them with SQL, like

```sql
SELECT foo,bar FROM 'baz.parquet' ...
```

## How to inspect word metadata from `documentNNNN.parquet` ?

The document keywords records contain repeated values. For debugging these
repeated values, they can be unnested in e.g. DuckDB with a query like

```sql
SELECT word, hex(wordMeta) from 
    (
        SELECT 
            UNNEST(word) AS word, 
            UNNEST(wordMeta) AS wordMeta 
        FROM 'document0000.parquet'
        WHERE url='...'
    )
WHERE word IN ('foo', 'bar')
```