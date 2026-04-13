# CLAUDE.md -- codec-http

## Purpose

HTTP codec with client/server actors, request/response model, and routing support.

## Dependencies

codec, serde, serde-json, handler

## Key Types

- `HttpClient` / `HttpServer` -- client and server actors
- `HttpClientRequest` / `HttpClientResponse` -- client-side Ask/Reply message pair
- `HttpRequest[P, B]` / `HttpResponse[C]` -- server-side request/response types
- `ClientCodec` / `ServerCodec` -- HTTP pipeline codec handlers
- `Router` / `RouterContext` / `RouterMatcher` -- HTTP routing
- `HttpMethod` / `HttpStatus` / `HttpVersion` / `MediaType` -- HTTP protocol enums
- `HttpHeaderKey` / `HttpHeaderValue` / `HttpHeaderUtil` -- header utilities
- `ParameterSerde[P]` -- URL/path parameter serialization

## Package Layout

```
cc.otavia.http           -- HTTP types, constants, routing
cc.otavia.http.client    -- HttpClient, ClientCodec, client messages
cc.otavia.http.server    -- HttpServer, ServerCodec, server messages, Router
```

## Testing

2 test files under `test/src/`.
