# CLAUDE.md -- sql-mysql-driver

## Purpose

MySQL database driver implementing the sql module's Connection/Driver SPI.

## Dependencies

sql

## Key Types

- `MySQLDriver` -- Driver SPI implementation
- `MySQLConnectOptions` -- MySQL-specific connection config (host, port, db, user, SSL mode)
- `MySQLException` -- MySQL error with message, error code, and SQL state
- `MySQLDatabaseMetadata` -- server metadata and capabilities
- `SslMode` -- enum of MySQL SSL modes (DISABLED, PREFERRED, REQUIRED, etc.)
- `AuthenticationPlugin` -- enum of MySQL auth plugins (native, caching_sha2)
- `Collation` -- enum of MySQL charset collation mappings with IDs
- `OkPacket` / `EofPacket` -- MySQL wire protocol response packets
- `Packets` / `CommandType` / `ColumnType` / `CapabilitiesFlag` -- protocol constants
- `Native41Authenticator` / `CachingSha2Authenticator` -- authentication handlers
- `RsaPublicKeyEncryptor` -- RSA encryption for password exchange

## Package Layout

```
cc.otavia.mysql              -- MySQLDriver, ConnectOptions, exceptions, enums
cc.otavia.mysql.protocol     -- wire protocol packets and constants
cc.otavia.mysql.spi          -- MySQLDriverFactory, MySQLServiceProvider
cc.otavia.mysql.utils        -- BufferUtils, authenticators, encryptor
```

## Testing

2 test files under `test/src/`.
