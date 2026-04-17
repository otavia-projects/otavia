# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Coding Agent Rules — MUST follow strictly

### Context Loading

1. **Load module CLAUDE.md files on demand.** Do NOT read all module CLAUDE.md files upfront. Use the Context Loading Guide below to determine which modules are relevant, then read only those.
2. **Read large source files incrementally.** Many files exceed 500 lines (e.g. `AbstractChannel.scala`, `AdaptiveBufferImpl.scala`, `ActorThread.scala`). First extract method/field signatures (via Grep, LSP, or other available tools), then read specific line ranges as needed. Do NOT read entire large files in one call.

### Before Implementing

3. **Study existing similar code first.** This project follows consistent patterns. You MUST find the closest existing implementation (e.g. new codec -> study codec-http) and follow its structure before writing any code.
4. **Clarify before coding.** For each task, identify the success criteria upfront — expected behavior, test cases, compilation. If you are unsure about requirements, approach, or which pattern to follow, ask the user. Never silently guess or assume. If something feels wrong or ambiguous, push back — do not paper over confusion with code.

### While Implementing

5. **Minimal, surgical changes.** Write the minimum code that solves the problem — no speculative features, no abstractions for single-use code, no error handling for impossible scenarios. Touch ONLY what you must — every changed line MUST trace directly to the user's request. If your implementation can be reduced to fewer lines while preserving correctness, rewrite it. If you notice unrelated dead code, mention it — do NOT delete it unless explicitly asked.
6. **Comments focus on code itself.** Scaladoc and inline comments MUST describe what the code does, its preconditions, and its effects. Do NOT include design rationale, decision history, comparison with alternatives, or meta-commentary about why something was written a certain way.

### After Implementing

7. **MUST compile and test after every change.** Run `./mill <module>.compile` after each logical change, then `./mill <module>.test` to verify. No exceptions. If compilation or tests fail, fix the code — NEVER modify tests to make broken code pass. Verify against the success criteria you defined before starting.

## Project Overview

Otavia is a high-performance IO & Actor programming model written in Scala 3. It provides a reactive framework for building scalable network applications. The IO stack is ported from Netty but integrates with an actor model. **This project is currently unstable.**

## Build System

Uses **Mill** as the build tool. Configuration is in `build.mill`. Requires **JDK 17+**.

### Common Commands

```bash
# Compile all modules
./mill __.compile

# Run tests for a specific package
./mill core.test -- --test-path "cc.otavia.core.actor"

# Run a specific test class
./mill core.test -- -o "cc.otavia.core.actor.MySpec"

# Clean build artifacts
./mill clean

# Run benchmarks
./mill buffer.bench.jmh
```

## Module Dependency Graph

Modules are layered — higher layers depend on lower ones. Each module links to its CLAUDE.md for details.

```
Layer 0 (Foundation, no internal deps):
  [common](common/CLAUDE.md)                          [otavia-common]
  [buffer](buffer/CLAUDE.md)                          [otavia-buffer]

Layer 1 (Core abstractions):
  [core](core/CLAUDE.md)          -> buffer, common   [otavia-runtime]
  [serde](serde/CLAUDE.md)        -> buffer, common   [otavia-serde]

Layer 2 (Framework):
  [codec](codec/CLAUDE.md)        -> core             [otavia-codec]
  [handler](handler/CLAUDE.md)    -> core, codec      [otavia-handler]
  [testkit](testkit/CLAUDE.md)    -> core             [otavia-testkit]
  [log4a](log4a/CLAUDE.md)        -> core             [otavia-log4a]
  [serde-json](serde-json/CLAUDE.md) -> serde         [otavia-serde-json]
  [serde-proto](serde-proto/CLAUDE.md) -> serde       [otavia-serde-proto]
  [sql](sql/CLAUDE.md)            -> core, codec, serde [otavia-sql]

Layer 3 (Protocol codecs):
  [codec-http](codec-http/CLAUDE.md)      -> codec, serde, serde-json, handler
  [codec-redis](codec-redis/CLAUDE.md)    -> core, codec, serde
  [codec-dns](codec-dns/CLAUDE.md)        -> core, codec
  [codec-mqtt](codec-mqtt/CLAUDE.md)      -> core, codec
  [codec-smtp](codec-smtp/CLAUDE.md)      -> core, codec
  [codec-socks](codec-socks/CLAUDE.md)    -> core, codec
  [codec-haproxy](codec-haproxy/CLAUDE.md) -> core, codec
  [codec-memcache](codec-memcache/CLAUDE.md) -> core, codec
  [codec-kafka](codec-kafka/CLAUDE.md)    -> core, codec (placeholder)

Layer 4 (SQL drivers):
  [sql-mysql-driver](sql-mysql-driver/CLAUDE.md)      -> sql   [otavia-mysql-driver]
  [sql-postgres-driver](sql-postgres-driver/CLAUDE.md) -> sql   [otavia-postgres-driver]

Aggregate: all -> (every published module)
```

**Package naming quirk**: The `codec` module's sources live under `cc.otavia.handler`, not `cc.otavia.codec`. Most protocol codec modules also use `cc.otavia.handler` for their base classes.

## Context Loading Guide

Read module CLAUDE.md files based on the task at hand. Each entry maps a concern to the relevant module(s).

| Task / Concern | Module(s) to Load |
|---|---|
| Actor lifecycle, message patterns, suspend/resume, stack coroutines | [core](core/CLAUDE.md) |
| ActorThread event loop, scheduling, mailbox priority | [core](core/CLAUDE.md) |
| Channel pipeline, handler masking, inflight mechanism | [core](core/CLAUDE.md) |
| Address routing, timer, object pooling, IoC | [core](core/CLAUDE.md) |
| Byte buffer operations, AdaptiveBuffer, memory allocation | [buffer](buffer/CLAUDE.md) |
| Adding a new serialization format, Serde/SerdeOps contracts | [serde](serde/CLAUDE.md) + target format module |
| JSON serialization, JsonSerde derived macro | [serde-json](serde-json/CLAUDE.md) |
| Writing a channel handler / codec | [codec](codec/CLAUDE.md) |
| SSL/TLS, idle timeout handlers | [handler](handler/CLAUDE.md) |
| HTTP server/client, routing | [codec-http](codec-http/CLAUDE.md) |
| Redis client, RESP protocol | [codec-redis](codec-redis/CLAUDE.md) |
| Database connectivity, SQL query API | [sql](sql/CLAUDE.md) + driver module |
| MySQL wire protocol, authentication | [sql-mysql-driver](sql-mysql-driver/CLAUDE.md) |
| PostgreSQL wire protocol, SCRAM auth | [sql-postgres-driver](sql-postgres-driver/CLAUDE.md) |
| Testing actors, TestProbe | [testkit](testkit/CLAUDE.md) |
| Logging, appender configuration | [log4a](log4a/CLAUDE.md) |
| Adding a new protocol codec | [codec](codec/CLAUDE.md) + reference: [codec-http](codec-http/CLAUDE.md) |

## Development Guidelines

### Scala 3 Specifics

- Scala version: **3.3.7**
- Compiler flag `-Yexplicit-nulls` is enabled — all nullable types must be explicitly handled
- Match types for compile-time message type safety (`ReplyOf`, `MessageOf`)
- Opaque types for message type encoding
- Macro-derived `JsonSerde` instances via inline macros

### Actor Implementation Patterns

```scala
// Pure business logic actor
class MyActor extends StateActor[MyCall]

// IO-capable actor
class MyIoActor extends ChannelsActor[MyCall]

// Message handling uses Stack/StackState for async patterns
// AskStack[A <: Ask[? <: Reply]] for request/response
// NoticeStack[M & Notice] for fire-and-forget
```

### Performance

- Prefer pooled buffers over heap allocation for hot paths
- Use object pools (`Poolable` trait) for high-frequency state objects (especially `StackState`)
- Leverage zero-copy operations in buffer handling
- `AdaptiveBuffer` avoids allocation for most read operations
- Envelopes, stacks, promises, and states are all object-pooled — no per-message heap allocation on hot paths
- `SpinLockQueue` used for cross-thread communication; `ConcurrentLinkedQueue` only where cross-thread insertion is rare

### Testing

- Framework: ScalaTest with `TestModule.ScalaTest`
- `testkit` module provides `TestProbe` for actor-level assertions with `askAndExpect`
- External dependencies: only `scalatest:3.2.19` for testing, `scala-xml:2.4.0` for log4a, `scram-client:2.1` for postgres

## Project Structure

Each module follows the standard Mill/Scala layout:
```
module-name/
├── src/cc/otavia/...
├── test/src/cc/otavia/...
└── resources/
```

CI runs on Ubuntu with JDK 17 (Zulu). Tests: `./mill __.test`. Docs: `./mill docs.site`. Publish: `./mill -i __.publishArtifacts`.
