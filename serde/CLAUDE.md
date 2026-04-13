# CLAUDE.md -- serde

## Purpose

A generic serialization framework. Defines `Serde[A]` as a format-agnostic serialization type class and `SerdeOps` as the primitive operations vocabulary that format implementations must provide. Any serialization format (JSON, Protobuf, MessagePack, etc.) can plug in by implementing these two interfaces.

## Dependencies

buffer, common

## Key Types

- `Serde[A]` -- format-agnostic type class: `serialize(value: A, out: Buffer)`, `deserialize(in: Buffer): A`, `checkDeserializable(in: Buffer): Boolean`
- `SerdeOps` -- primitive operations vocabulary (self-type: Serde[?]). Defines serialize/deserialize for all primitive types (Byte, Boolean, Int, Long, String, BigDecimal, Instant, UUID, etc.). Macro-generated code calls these methods to encode/decode fields
- `@ignore` / `@rename(name)` / `@stringfield` -- format-neutral field annotations, interpreted at compile time by each format module's macro
- `BytesSerde` / `StringSerde` -- raw binary/string pass-through serdes (no format encoding)

## Package Layout

```
cc.otavia.serde              -- Serde[A], SerdeOps
cc.otavia.serde.annotation   -- @ignore, @rename, @stringfield
cc.otavia.serde.helper       -- BytesSerde, StringSerde
```

## Format Plugin Architecture

A format module (e.g. serde-json, serde-proto) plugs in by:
1. Defining a format-specific trait (e.g. `JsonSerde[A] extends Serde[A] with SerdeOps`) that implements all SerdeOps primitive methods with format-specific encoding logic
2. Providing a compile-time macro derivation (e.g. `JsonSerde.derived[T]`) that reads `@ignore`/`@rename`/`@stringfield` annotations and generates format-specific serialize/deserialize code
3. Providing `given` instances for standard library types

Any code that is polymorphic over `Serde[A]` works with any format without knowing which one is in use. The annotations are a shared declarative layer; each format's macro interprets them in its own way.

## Testing

No dedicated test directory. serde-json and serde-proto tests exercise the Serde interfaces.
