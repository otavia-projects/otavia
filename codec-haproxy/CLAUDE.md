# CLAUDE.md -- codec-haproxy

## Purpose

HAProxy proxy protocol codec (early stage).

## Dependencies

core, codec

## Key Types

- `HAProxyCommand` -- enum of HAProxy protocol commands (LOCAL, PROXY)
- `HAProxyConstants` -- constants for HAProxy protocol header formatting

## Package Layout

```
cc.otavia.handler.codec.haproxy -- HAProxyCommand, HAProxyConstants
```

## Testing

No tests yet.
