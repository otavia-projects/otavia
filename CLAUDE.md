# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Otavia is a high-performance IO & Actor programming model written in Scala 3. It provides a modern, reactive framework for building scalable network applications with a focus on performance and low resource consumption.

**Important**: This project is currently unstable. Use caution in production environments.

## Build System

This project uses **Mill** as the build tool. The main build configuration is in `build.mill`.

### Common Development Commands

#### Building and Compilation
```bash
# Compile all modules
./mill __.compile

# Compile specific module
./mill core.compile

# Clean all build artifacts
./mill clean

# Clean specific module
./mill core.clean
```

#### Testing
```bash
# Run all tests
./mill __.test

# Run tests for specific module
./mill core.test
./mill buffer.test

# Run tests with specific arguments
./mill core.test -- --test-path "cc.otavia.core.actor"
```

#### Running Benchmarks
```bash
# Run buffer benchmarks
./mill buffer.bench.jmh
```

#### Publishing and Assembly
```bash
# Create JAR files for all modules
./mill __.assembly

# Create JAR for specific module
./mill core.assembly

# Publish locally
./mill __.publishLocal
```

## Project Architecture

The project follows a modular architecture with clear separation of concerns:

### Core Modules
- **`common`** - Shared utilities and build information
- **`buffer`** - High-performance buffer management and memory allocation
- **`core`** - Core runtime including Actor system, channels, and message handling
- **`serde`** - Serialization framework with support for JSON and Protocol Buffers

### Codec Modules
- **`codec`** - Base codec abstractions and byte-to-message transformation
- **`codec-http`** - HTTP/HTTPS client and server implementation
- **`codec-redis`** - Redis protocol client with pub/sub support
- **`codec-mysql`** - MySQL database driver
- **`codec-postgres`** - PostgreSQL database driver
- **`codec-dns`** - DNS protocol implementation
- **`codec-mqtt`** - MQTT protocol support
- **`codec-smtp`** - SMTP protocol support
- **`codec-socks`** - SOCKS proxy protocol
- **`codec-haproxy`** - HAProxy protocol
- **`codec-memcache`** - Memcached protocol
- **`codec-kafka`** - Apache Kafka protocol

### Supporting Modules
- **`handler`** - Channel handlers and business logic abstractions
- **`log4a`** - Asynchronous logging framework
- **`sql`** - SQL abstraction layer
- **`testkit`** - Testing utilities and frameworks
- **`all`** - Aggregate module containing all dependencies

### Key Architectural Patterns

#### Message Model
Otavia uses a type-safe message system with three core message types:
- **`Notice`** - Fire-and-forget messages (no reply expected)
- **`Ask[R <: Reply]`** - Request messages expecting a `Reply` response
- **`Reply`** - Response messages for `Ask` requests

Messages are sent via `Address` instances (not `Actor` instances directly), ensuring compile-time type safety.

#### Actor System
The core actor system in `core/src/cc/otavia/core/actor/` provides:
- **`StateActor`** - Generic actor for state management and message processing
- **`ChannelsActor`** - Actor for managing IO channels (TCP, UDP, file)
- **`AcceptorActor`** - Actor for accepting TCP connections with worker routing
- Message execution via `Stack` (similar to continuations)
- Actor lifecycle managed by `ActorSystem` with mount/unmount hooks

#### Stack and StackState
- Messages are executed via `Stack` objects, not direct method calls
- `AskStack` for request/response, `NoticeStack` for fire-and-forget
- `StackState` enables resumable execution with async/await patterns
- Object pooling for zero-allocation message handling

#### Buffer Management
The buffer system in `buffer/src/cc/otavia/buffer/` features:
- Pooled and unpooled memory allocators
- Direct and heap memory management
- **`AdaptiveBuffer`** - Auto-expanding buffer (replaces Netty's `CompositeBuffer`)
- Zero-copy operations where possible

#### Channel Architecture
The channel system in `core/src/cc/otavia/core/channel/` provides:
- Non-blocking I/O operations ported from Netty
- Pipeline-based processing (`ChannelPipeline` with `ChannelHandler`s)
- **Inflight mechanism** - Manages concurrent requests per connection (pipelining)
  - Uses `QueueMap[V <: QueueMapEntity]` for high-performance concurrent request tracking
  - Barrier mode for atomic operations
- `ChannelFuture` and `ChannelStack` for async IO operations
- `Reactor` handles IO events, sends `ReactorEvent` to `ChannelsActor`

#### IoC (Inversion of Control)
Located in `core/src/cc/otavia/core/ioc/`, the IoC container provides:
- Compile-time type-safe dependency injection using Scala 3 match types
- `ActorSystem` acts as a container for `Actor` instances
- Inject `Actor` dependencies via type signatures
- `BeanManager` handles actor registration and lookup with qualifier-based injection
- Primary bean support and super-type resolution for dependency resolution

#### Actor Threading Model
- `ActorThread`-per-actor execution model - each actor runs on its own thread
- Zero-context-switch message processing within actor boundaries
- Messages are executed via `Stack` objects, not direct method calls
- Actor lifecycle managed by `ActorHouse` with mount/unmount hooks

#### Exception Handling
Exception strategies defined in `core/src/cc/otavia/core/actor/`:
- `Restart` - Restart the actor (calls `beforeRestart()`, `restart()`, `afterRestart()`)
- `Ignore` - Ignore the exception and continue
- `ShutdownSystem` - Shut down the entire actor system

## Development Guidelines

### Message Sending Patterns
- Messages can inherit both `Notice` and `Ask` traits
- How you send determines the type: `address.notice(msg)` vs `address.ask(msg)`
- Use `StackState` with `Future` for async operations within actors
- Use object pools (via `Poolable` trait) for high-frequency state objects
- Message type safety enforced at compile time via `MessageOf[A <: Actor[?]]` type alias

### Actor Implementation
- Extend `StateActor[M <: Call]` for stateful actors without channel support
- Extend `ChannelsActor[M <: Call]` for actors that need to manage IO channels
- Use `AskStack[A <: Ask[? <: Reply]]` for request/response patterns
- Use `NoticeStack[M & Notice]` for fire-and-forget message handling
- Implement lifecycle hooks: `afterMount()`, `beforeRestart()`, `restart()`, `afterRestart()`

### Module Dependencies
- Always depend on abstractions, not implementations
- Use `core` for actor system functionality
- Use `buffer` for memory management
- Use appropriate codec modules for protocol support
- Include `testkit` for writing tests

### Performance Considerations
- Prefer pooled buffers over heap allocation for hot paths
- Use the actor system for concurrent operations
- Leverage zero-copy operations in buffer handling
- Consider async patterns for I/O-bound operations
- Use object pools for `StackState` to minimize GC pressure

### Testing Strategy
- Write unit tests using ScalaTest
- Use `testkit` for actor system testing
- Test both success and failure scenarios
- Include performance regression tests for critical paths

## Scala Version

This project uses Scala 3.3.7 with the `-Yexplicit-nulls` compiler flag enabled for better null safety.

## Type System Notes

Otavia leverages Scala 3's advanced type system:
- **Match types** for compile-time message type safety
- **Opaque types** for message type encoding (`MessageOf[A <: Actor[?]]`)
- **Higher-kinded types** for generic actor patterns
- Messages are sealed traits: `Call extends Message`, with subtypes `Notice`, `Ask[R <: Reply]`, and `Reply`

## Project Structure

Each module follows the standard Mill/Scala structure:
```
module-name/
├── src/
│   └── cc/otavia/.../
├── test/
│   └── src/
└── resources/
```