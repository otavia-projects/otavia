# CLAUDE.md -- codec

## Purpose

Concrete codec implementations for the channel pipeline. Abstract codec bases (`ByteToByteDecoder`, `ByteToMessageDecoder`, etc.) live in the core module at `cc.otavia.core.channel.handler`.

## Dependencies

core

## Key Types

- `FixedLengthFrameDecoder` -- splits received bytes by fixed frame length
- `Base64Encoder` / `Base64Decoder` / `Base64` / `Base64Dialect` -- Base64 codec
- `StringDecoder` / `StringEncoder` / `LineEncoder` / `LineSeparator` -- string codec
- `ByteArrayDecoder` / `ByteArrayEncoder` -- byte array codec
- `CodecException` / `DecoderException` / `EncoderException` -- codec error hierarchy
- `CorruptedFrameException` / `TooLongFrameException` -- frame-level exceptions

## Package Layout

```
cc.otavia.handler.codec              -- codec exceptions
cc.otavia.handler.codec.base64       -- Base64 encode/decode
cc.otavia.handler.codec.bytes        -- byte array codec
cc.otavia.handler.codec.compression  -- compression exceptions (no implementations yet)
cc.otavia.handler.codec.string       -- string and line codec
```

## Patterns

- Abstract codec bases are in core module (`cc.otavia.core.channel.handler`), not here
- Sources live under `cc.otavia.handler` package, NOT `cc.otavia.codec` -- this is a naming quirk
- Protocol codec modules (codec-http, codec-redis, etc.) also use `cc.otavia.handler` for base classes

## Testing

1 test file under `test/src/`.
