# CLAUDE.md -- codec-redis

## Purpose

Redis client with RESP protocol codec and command framework.

## Dependencies

core, codec, serde

## Key Types

- `Client` -- Redis client actor
- `RedisCodec` / `RedisMessage` -- RESP protocol codec
- `Command[R]` / `CommandResponse` -- Redis command Ask/Reply types
- `RedisSerde[T]` / `AbstractCommandSerde[C]` / `AbstractResponseSerde[R]` -- command/response serde
- `RedisProtocolException` / `CommandException` -- error types

## Package Layout

```
cc.otavia.handler.codec.redis  -- RedisCodec, RedisMessage
cc.otavia.redis                -- Client, command types
cc.otavia.redis.cmd            -- Command definitions
cc.otavia.redis.serde          -- Redis serde hierarchy
cc.otavia.redis.serde.impl     -- concrete command/response serdes
```

## Testing

No dedicated test files yet.
