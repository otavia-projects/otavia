# CLAUDE.md -- codec-socks

## Purpose

SOCKS protocol codec (early stage).

## Dependencies

core, codec

## Key Types

- `SocksAddressType` -- enum of SOCKS address types (IPv4, IPv6, domain)
- `SocksVersion` -- enum of SOCKS protocol versions (SOCKS4a, SOCKS5)

## Package Layout

```
cc.otavia.handler.codec.socks   -- SocksAddressType
cc.otavia.handler.codec.socksx  -- SocksVersion
```

## Testing

No tests yet.
