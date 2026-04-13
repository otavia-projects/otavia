# CLAUDE.md -- sql

## Purpose

SQL abstraction layer (Actor Database Connect -- ADBC) defining connection, query, result-set interfaces, and driver SPI.

## Dependencies

core, codec, serde

## Key Types

- `Connection` -- manages a single database connection for query execution
- `ConnectOptions` -- base class for connection configuration parameters
- `Driver` / `DriverFactory` / `DriverManager` -- SPI driver discovery via ServiceLoader
- `Row` -- trait for a single database row as a typed Product/Reply
- `RowSet[R]` -- case class containing an array of decoded Row results
- `RowCodec[R]` -- trait for row encoding/decoding with macro derivation
- `RowParser` / `RowWriter` -- reading/writing column values
- `ColumnDescriptor` -- metadata for a single column
- `SimpleQuery[T]` / `PrepareQuery[T]` / `ModifyRows` -- SQL statement types
- `Cursor` / `ExecuteCursor[R]` / `CursorRow[R]` / `CursorEnd` -- cursor-based query messages
- `RowSerde[R]` -- serde integrating RowParser for row serialization
- `ProxyType` / `ProxyOptions` -- proxy configuration

## Package Layout

```
cc.otavia.sql             -- Connection, Driver, Row, RowSet, statement types
cc.otavia.sql.impl        -- internal utilities
cc.otavia.sql.net         -- proxy configuration
cc.otavia.sql.serde       -- RowSerde
cc.otavia.sql.spi         -- ADBCServiceProvider
cc.otavia.sql.statement   -- SimpleQuery, PrepareQuery, ModifyRows
```

## Testing

2 test files under `test/src/`.
