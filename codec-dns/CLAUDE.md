# CLAUDE.md -- codec-dns

## Purpose

DNS protocol codec for query encoding and resolution via UDP datagrams.

## Dependencies

core, codec

## Key Types

- `DatagramDnsQueryResolver` -- actor that resolves DNS queries via UDP
- `DnsQueryEncoder` -- encoder for DNS query messages

## Package Layout

```
cc.otavia.dns         -- DatagramDnsQueryResolver
cc.otavia.dns.codec   -- DnsQueryEncoder
```

## Testing

No tests yet.
