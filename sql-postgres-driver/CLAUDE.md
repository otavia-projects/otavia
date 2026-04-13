# CLAUDE.md -- sql-postgres-driver

## Purpose

PostgreSQL database driver implementing the sql module's Connection/Driver SPI.

## Dependencies

sql. External: `scram-client:2.1` for SCRAM-SHA-256 authentication.

## Key Types

- `PostgresDriver` -- Driver SPI implementation
- `PostgresConnectOptions` -- PostgreSQL-specific connection config
- `PostgresException` -- full PostgreSQL ErrorResponse fields
- `PostgresDatabaseMetadata` -- server metadata and capabilities
- `SslMode` -- enum of PostgreSQL SSL modes (DISABLE, PREFER, REQUIRE, etc.)
- `PostgresRowParser` / `PostgresRowWriter` -- row encoding/decoding
- `PreparedStatement` -- prepared statement handling
- `DataType` / `DataFormat` / `Constants` -- PostgreSQL wire protocol types
- `ScramAuthentication` / `MD5Authentication` -- authentication mechanisms
- `TransactionFailed` / `RowDecodeException` -- error types

## Package Layout

```
cc.otavia.postgres              -- PostgresDriver, ConnectOptions, exceptions
cc.otavia.postgres.impl         -- PostgresRowParser, PreparedStatement, RowDesc
cc.otavia.postgres.protocol     -- DataType, DataFormat, Constants
cc.otavia.postgres.spi          -- PostgresDriverFactory, PostgresServiceProvider
cc.otavia.postgres.utils        -- PgBufferUtils, ScramAuthentication, MD5Authentication
```

## Testing

No test files yet.
