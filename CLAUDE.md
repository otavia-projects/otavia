# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Otavia is a high-performance IO & Actor programming model written in Scala 3. It provides a reactive framework for building scalable network applications. The IO stack is ported from Netty but integrates with an actor model. **This project is currently unstable.**

## Build System

Uses **Mill** as the build tool. Configuration is in `build.mill`. Requires **JDK 17+**.

### Common Commands

```bash
# Compile all modules
./mill __.compile

# Compile specific module
./mill core.compile

# Run all tests
./mill __.test

# Run tests for specific module
./mill core.test
./mill buffer.test

# Run tests for a specific package
./mill core.test -- --test-path "cc.otavia.core.actor"

# Run a specific test class
./mill core.test -- -o "cc.otavia.core.actor.MySpec"

# Clean build artifacts
./mill clean

# Create JAR files
./mill __.assembly
./mill core.assembly

# Publish locally
./mill __.publishLocal

# Run benchmarks
./mill buffer.bench.jmh
```

## Module Dependency Graph

Modules are layered — higher layers depend on lower ones. Understanding these layers is essential when adding cross-module code.

```
Layer 0 (Foundation, no internal deps):
  common          [otavia-common]
  buffer          [otavia-buffer]

Layer 1 (Core abstractions):
  core            -> buffer, common          [otavia-runtime]
  serde           -> buffer, common          [otavia-serde]

Layer 2 (Framework):
  codec           -> core                    [otavia-codec]
  handler         -> core, codec             [otavia-handler]
  testkit         -> core                    [otavia-testkit]
  log4a           -> core                    [otavia-log4a]
  serde-json      -> serde                   [otavia-serde-json]
  serde-proto     -> serde                   [otavia-serde-proto]
  sql             -> core, codec, serde      [otavia-sql]

Layer 3 (Protocol codecs):
  codec-http      -> codec, serde, serde-json, handler
  codec-redis     -> core, codec, serde
  codec-dns       -> core, codec
  codec-mqtt      -> core, codec
  codec-smtp      -> core, codec
  codec-socks     -> core, codec
  codec-haproxy   -> core, codec
  codec-memcache  -> core, codec
  codec-kafka     -> core, codec             (empty — no source files yet)

Layer 4 (SQL drivers):
  sql-mysql-driver      -> sql               [otavia-mysql-driver]
  sql-postgres-driver   -> sql               [otavia-postgres-driver]

Aggregate: all -> (every published module)
```

**Package naming quirk**: The `codec` module's sources live under `cc.otavia.handler`, not `cc.otavia.codec`. Most protocol codec modules also use `cc.otavia.handler` for their base classes.

## Architecture

### Message Model

Type-safe message system with three core types (all in `core/src/cc/otavia/core/message/`):
- **`Notice`** — fire-and-forget, no reply expected
- **`Ask[R <: Reply]`** — request expecting a typed `Reply` response
- **`Reply`** — response to an `Ask`

Messages are sent via `Address` instances (never directly to `Actor` refs). `address.notice(msg)` vs `address.ask(msg)` determines the pattern. The match type `ReplyOf[A <: Ask[? <: Reply]]` extracts the reply type at compile time. Messages are wrapped in `Envelope[T]` objects (sender address, monotonic message ID, payload) — envelopes are object-pooled.

### Actor System

#### Actor Hierarchy

```
Actor[M <: Call]                    (trait — lifecycle hooks, receive methods)
  └─ AbstractActor[M <: Call]       (extends FutureDispatcher — stack mechanics)
       ├─ StateActor[M <: Call]     (pure business logic, rejects ChannelStack)
       └─ ChannelsActor[M <: Call]  (IO-capable, manages Channel instances)
            ├─ SocketChannelsActor
            ├─ DatagramChannelsActor
            └─ AcceptorActor         (TCP acceptor with worker routing)
```

All files in `core/src/cc/otavia/core/actor/`. Lifecycle hooks: `afterMount()`, `beforeRestart()`, `restart()`, `afterRestart()`. Exception strategies: `Restart`, `Ignore`, `ShutdownSystem`.

#### Mailbox and Dispatch Priority

Each actor (`ActorHouse`) has **five separate mailboxes**: notice, ask, reply, exception, event. Dispatch in `ActorHouse.run()` processes them in priority order:

1. **Replies first** — resumes suspended stacks (most latency-sensitive)
2. **Exceptions** — delivers failure to waiting promises
3. **Asks** — respects barrier state
4. **Notices** — respects barrier state
5. **Events** — timer/reactor events
6. **Channel inflight futures** — pending ChannelFuture completions

**Barrier mechanism**: When processing a barrier-marked Ask (`isBarrier(call)`), the actor sets `inBarrier = true` and only processes replies/events until the barrier clears. This enables atomic operation semantics.

**Batch mode**: When `actor.batchable` is `true`, messages are collected into a `mutable.ArrayBuffer` via thread-local reusable buffers and dispatched as `receiveBatchNotice`/`receiveBatchAsk`.

#### ActorHouse State Machine

```
CREATED → MOUNTING → WAITING → READY → SCHEDULED → RUNNING → (back to WAITING or READY)
```

### Stack-Based Continuations

Messages execute via `Stack` objects (not direct method calls), enabling resumable execution similar to CPS. All stack types are object-pooled via `ActorThreadIsolatedObjectPool`.

**Stack types** (in `core/src/cc/otavia/core/stack/`):
- `AskStack[A]` — handles Ask messages, provides `return(reply)`
- `NoticeStack[N]` — handles Notice messages, provides `return()`
- `BatchAskStack` / `BatchNoticeStack` — batched variants
- `ChannelStack[T]` — IO channel processing, implements `QueueMapEntity`

**Suspend/Resume flow**:
1. User code calls `stack.suspend(someState)` → returns `StackYield.SUSPEND`
2. The stack waits with its uncompleted promises in a doubly-linked list
3. When a reply arrives, `receiveReply()` completes the promise and re-dispatches the stack
4. User code resumes via `match` on `stack.state.id`

**State helpers** (in `core/src/cc/otavia/core/stack/helper/`):
- `FutureState[R <: Reply](stateId: Int)` — holds a `MessageFuture[R]`, for waiting on a single actor reply
- `FuturesState[R]` — holds `Seq[MessageFuture[R]]`, for waiting on multiple replies
- `ChannelFutureState` — holds a `ChannelFuture`, for IO operations

**Typical pattern**:
```scala
protected def resumeAsk(stack: AskStack[MyAsk]): StackYield =
  val ask = stack.ask
  val state = FutureState[MyReply](1)  // stateId for match
  targetAddress.ask(ask)(using state.future, this)
  stack.suspend(state)
```

### Threading Model

Two thread pools work together:

1. **ActorThread pool** — runs actor message processing, each thread also owns an `NioHandler` for IO readiness
2. **NioReactor worker pool** — dedicated NIO selection threads that detect channel readiness

**Event loop** per `ActorThread` (`core/src/cc/otavia/core/system/ActorThread.scala`):
1. **IO phase**: `ioHandler.run()` polls NIO events
2. **IO pipeline phase**: `ChannelsActor` processing (drained completely, no deadline)
3. **Business logic phase**: `StateActor` message processing (bounded by time budget)

The `ioRatio` config (default 50) controls the split: StateActors get `ioTime * (100 - ioRatio) / ioRatio` nanoseconds (minimum 500μs). All actors on the same `ActorThread` are single-threaded with respect to each other — no locks needed.

**Scheduling** via `HouseManager` with three queues: mounting (FIFO), channelsActor (priority), actor (priority). `PriorityHouseQueue` uses a two-queue design (normal + high priority), where houses receiving replies get promoted to high priority. **Work stealing**: threads with excess ready actors can have work stolen by other threads.

**IO event flow**: NIO reactor workers detect channel readiness → send `ReactorEvent` via `channel.executorAddress.inform()` → the owning ActorThread processes the event in its ChannelsActor pipeline.

**Reactor thread routing** (`NioThreadChoicer` strategies): `OneByOneNioThreadChoicer` (1:1 mapping), `RandomNioThreadChoicer` (hash-based), `PreferentialNioThreadChoicer` (block mapping).

### Channel Architecture

The channel system (in `core/src/cc/otavia/core/channel/`) provides:
- **Pipeline**: doubly-linked list of `ChannelHandlerContext` nodes, bounded by HeadHandler (outbound ops → transport) and TailHandler (inbound → `channel.onInboundMessage()`)
- **Handler masking**: each handler gets a bitmask at registration; the pipeline skips handlers whose mask doesn't match the event type. Use `@Skip` annotation on default implementations to exclude them from the mask
- **AdaptiveBuffer management**: buffered handlers get `AdaptiveBuffer` instances; non-buffered handlers cannot precede buffered ones
- **`@Sharable`** annotation required for handlers added to multiple pipelines; `checkMultiplicity()` prevents duplicate non-sharable handlers

**Inflight mechanism** (`core/src/cc/otavia/core/channel/inflight/`):
- `QueueMap[V <: QueueMapEntity]` — dual structure: hash table for O(1) lookup by `entityId` + ordered insertion queue for FIFO
- Two inflight maps per pipeline: `inflightFutures: QueueMap[ChannelPromise]` and `inflightStacks: QueueMap[ChannelStack]`
- Barrier mode for atomic operations

**TransportFactory** uses `java.util.ServiceLoader` to discover providers, defaults to NIO. Creates channel types: `NioServerSocketChannel`, `NioSocketChannel`, `NioDatagramChannel`, `NioFileChannel`.

### Address System

Address types (in `core/src/cc/otavia/core/address/`):
- **`ActorAddress`** — one per actor, routes directly to `ActorHouse` mailboxes
- **`RobinAddress`** — round-robin over `ActorAddress[]`; smart routing for Ask messages: if sender is on a load-balanced actor, routes to the same thread's instance for data locality
- **`ActorThreadAddress`** — routes events to a specific `ActorThread`'s event queue (used by timer/reactor)
- **`ProxyAddress`** — marker trait for proxying addresses; `reply()`/`throw()` throw (replies should never arrive at a proxy)

Actor creation (`ActorSystemImpl`): `num == 1` → single instance; `num == pool.size` → one per thread with `RobinAddress`; `num > 1` → round-robin with `RobinAddress`.

### Timer System

`HashedWheelTimer` (default: 100ms tick, 512 slots) runs on a dedicated thread. Registration methods: `registerActorTimeout`, `registerChannelTimeout`, `registerAskTimeout`, `registerResourceTimeout`.

Timeout trigger modes: `FixTime(date)`, `DelayTime(delay, unit)`, `DelayPeriod(delay, period)`, `FirstTimePeriod(first, period)`. Long-lived timeouts (remainingRounds >= 4) are stored in a separate linked list to avoid wasteful round-counting.

`TimerTaskManager` tracks active tasks in a `ConcurrentHashMap[Long, TimeoutTask]`, supports `update()` to re-time triggers.

### Object Pooling

The cache system (in `core/src/cc/otavia/core/cache/`) provides zero-allocation message processing:

- **`Poolable`** — base trait extending `Nextable`, tracks creator thread, has `recycle()` and `cleanInstance()`
- **`ActorThreadIsolatedObjectPool`** — per-actor-thread holders, drops objects recycled by non-creator threads, auto-cleans holders with >10 objects every 60s
- **`ThreadLocal[V]`** — array-indexed storage (not JDK hash-based) via `ActorThread.index`, O(1) access

Thread-local reusable collections on `ActorThread`: `threadBuffer[T]`, `threadSet[T]`, `threadMap[K,V]` — avoid allocation during batch processing.

### IoC (Inversion of Control)

Located in `core/src/cc/otavia/core/ioc/`:
- `BeanManager` stores actors by class name in `ConcurrentHashMap`, supports qualifier-based and supertype-based lookup, `@Primary` for resolving multiple implementations
- `Module`/`AbstractModule` define `BeanDefinition` sequences; `ModuleListener` callbacks on load
- `@Component(num)` marks auto-created actors

### Buffer Management

The buffer system (in `buffer/src/cc/otavia/buffer/`) features:
- Pooled and unpooled memory allocators (direct and heap)
- **`AdaptiveBuffer`** — auto-expanding buffer (equivalent to Netty's `CompositeByteBuf`)
- Thread-local `DirectPooledPageAllocator` and `HeapPooledPageAllocator` per `ActorThread`
- Zero-copy operations where possible

### Serde (Serialization)

The `Serde[A]` type class (in `serde/src/cc/otavia/serde/`) defines `serialize`, `deserialize`, and `checkDeserializable`. Two format modules:
- **`serde-json`** — `JsonSerde[A] extends Serde[A]`, with macro-derived instances via `JsonSerde.derived[T]`
- **`serde-proto`** — `ProtoSerde[A] extends Serde[A]` (interface only, implementation in progress)

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
