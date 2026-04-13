# CLAUDE.md -- handler

## Purpose

SSL/TLS handlers and timeout handlers for channel pipelines.

## Dependencies

core, codec

## Key Types

### SSL (`cc.otavia.handler.ssl`)
- `SslHandler` -- channel handler managing SSL/TLS handshake and encryption
- `SslContext` / `SslContextBuilder` -- abstract factory and fluent builder for SSL context
- `JdkSslContext` / `JdkSslClientContext` / `JdkSslServerContext` -- JDK-based SSL implementations
- `JdkSslEngine` / `JdkAlpnSslEngine` -- SSLEngine wrappers with ALPN support
- `ApplicationProtocolConfig` / `ApplicationProtocolNegotiator` -- ALPN negotiation
- `CipherSuiteFilter` / `IdentityCipherSuiteFilter` / `SupportedCipherSuiteFilter` -- cipher filtering
- `SslHandshakeCompletion` / `SslCloseCompletion` -- SSL lifecycle events

### Timeout (`cc.otavia.handler.timeout`)
- `IdleStateHandler` -- triggers idle state events based on read/write inactivity
- `IdleState` / `IdleStateEvent` -- idle state enum and event type

## Package Layout

```
cc.otavia.handler.ssl             -- SSL/TLS context, handler, engine
cc.otavia.handler.ssl.protocol    -- ALPN negotiation protocol
cc.otavia.handler.timeout         -- idle state detection
```

## Testing

2 test files under `test/src/`.
