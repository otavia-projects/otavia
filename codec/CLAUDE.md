# CLAUDE.md -- codec

## Purpose

Base codec framework providing abstract encoder/decoder handlers for the channel pipeline. Concrete codecs (length framing, Base64, string, byte array) included.

## Dependencies

core

## Key Types

- `ByteToByteCodec` / `ByteToByteDecoder` / `ByteToByteEncoder` -- byte-level transform handlers
- `ByteToMessageCodec` / `ByteToMessageDecoder` / `MessageToByteEncoder` -- byte-to-message bridges
- `FixedLengthFrameDecoder` -- splits received bytes by fixed frame length
- `Base64Encoder` / `Base64Decoder` / `Base64` / `Base64Dialect` -- Base64 codec
- `StringDecoder` / `StringEncoder` / `LineEncoder` / `LineSeparator` -- string codec
- `ByteArrayDecoder` / `ByteArrayEncoder` -- byte array codec
- `CodecException` / `DecoderException` / `EncoderException` -- codec error hierarchy
- `CorruptedFrameException` / `TooLongFrameException` -- frame-level exceptions

## Package Layout

```
cc.otavia.handler.codec              -- base codec abstractions
cc.otavia.handler.codec.base64       -- Base64 encode/decode
cc.otavia.handler.codec.bytes        -- byte array codec
cc.otavia.handler.codec.compression  -- compression exceptions (no implementations yet)
cc.otavia.handler.codec.string       -- string and line codec
```

## Patterns

- Sources live under `cc.otavia.handler` package, NOT `cc.otavia.codec` -- this is a naming quirk
- Protocol codec modules (codec-http, codec-redis, etc.) also use `cc.otavia.handler` for base classes

## Testing

1 test file under `test/src/`.
