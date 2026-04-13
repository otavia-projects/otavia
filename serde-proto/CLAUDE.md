# CLAUDE.md -- serde-proto

## Purpose

Protobuf serialization interface (implementation in progress).

## Dependencies

serde

## Key Types

- `ProtoSerde[A] extends Serde[A]` -- protobuf format serde trait (interface only)

## Package Layout

```
cc.otavia.proto -- ProtoSerde
```

## Testing

1 test file under `test/src/`.
