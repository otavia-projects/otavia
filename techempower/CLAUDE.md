# CLAUDE.md -- techempower

## Purpose

TechEmpower Framework Benchmarks implementation for Otavia. Covers the following benchmark test types:

| Test | Endpoint | Description |
|------|----------|-------------|
| Test 1 | `GET /json` | JSON serialization |
| Test 2 | `GET /db` | Single database query |
| Test 3 | `GET /queries` | Multiple database queries |
| Test 4 | `GET /fortunes` | Fortunes (template rendering) |
| Test 5 | `GET /updates` | Database updates |
| Test 6 | `GET /plaintext` | Plaintext |

## Dependencies

Depends on `codec-http` and `sql-postgres-driver`.

## Source Layout

```
src/app/
├── startup.scala              — Main entry point, server bootstrap, router definitions
├── controller/
│   ├── DBController.scala     — Tests 2, 3, 5: single/multi/batch DB queries via PostgreSQL
│   ├── FortuneController.scala— Test 4: fortune query + sorting + HTML rendering
│   └── JsonController.scala   — Tests 1, 6: JSON serialization and plaintext response
├── model/model.scala          — Data models: World, Fortune, Message with derived RowCodec/JsonSerde
└── util/FortunesRender.scala  — Hand-rolled HTML serde for fortunes (zero-copy buffer writes, HTML escaping)
```

## Key Patterns

- **Startup**: `@main def startup(url, user, password, poolSize)` — creates `ActorSystem`, wires connection pool and controllers via `buildActor`, then launches `HttpServer` on port 8080.
- **Controllers**: Each extends `StateActor[Req]` and handles requests via `resumeAsk` with `Stack`/`StackState` suspend-resume for async DB operations.
- **DB access**: Uses `PrepareQuery.fetchOne`/`fetchAll`/`updateBatch` against PostgreSQL. Connection pool is autowired via IoC (`autowire[Connection]()`).
- **Fortune rendering**: `FortunesRender` is a custom `Serde[Array[Fortune]]` that writes pre-encoded UTF-8 HTML fragments directly to `Buffer` — no template engine, no string allocation on hot path.
- **Routing**: Routes defined in `startup.scala` using `constant`, `get` helpers from `Router`. Each route maps to a controller and specifies its `HttpResponseSerde`.

