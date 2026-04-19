# CLAUDE.md -- core

## Purpose

The runtime kernel of Otavia. Encapsulates the complexity of IO threading, actor scheduling, stack-based coroutines, channel pipelines, and object pooling behind a simple message-driven programming interface.

## Dependencies

buffer, common

---

## User API vs Internal Kernel

The framework is split into two layers: the **user programming interface** (simple) and the **internal kernel** (complex).

### What the user writes

```scala
// 1. Define message types
case class GetUser(id: Long) extends Ask[User]
case class User(name: String) extends Reply

// 2. Define an actor -- implement resumeAsk / resumeNotice
class UserService extends StateActor[Call] {
    override protected def resumeAsk(stack: AskStack[GetUser]): StackYield =
        stack.state match
            case _: StartState =>
                val state = FutureState[User](1)
                dbAddress.ask(GetUser(42), state.future)
                stack.suspend(state)
            case state: FutureState[User] =>
                stack.return(state.future.getNow)
}
```

The user only needs to: define messages, match on state, call suspend/return. Everything else is handled by the kernel.

### What the kernel handles (invisible to the user)

- Object pool allocation/recycling of Stacks, Envelopes, Promises, States
- Envelope packaging, message ID generation
- Promise creation, attaching to Stack, waking up on completion
- Mailbox priority dispatch (replies before asks) and scheduling priority (reply backlog, event backlog, leaf-actor detection)
- ActorThread three-phase event loop scheduling (IO / ChannelsActor / StateActor)
- Channel inflight management (pendingFutures / inflightFutures / pendingStacks / inflightStacks)
- Cross-thread message delivery (synchronized Mailbox.put + ioHandler.wakeup)
- Zero allocation for all intermediate state objects on hot paths

---

## Execution Model

### ActorThread Three-Phase Event Loop

Each ActorThread is simultaneously the IO thread and the actor execution thread. The event loop has three phases:

1. **Phase 1 -- IO**: `ioHandler.run()` polls IO events (select); TCP reads execute the channel pipeline synchronously
2. **Phase 2 -- ChannelsActor dispatch** (no time budget, fully drained): processes `pendingChannels` -- dispatches ChannelStacks to user code, processes inflight futures/stacks, flushes outbound writes
3. **Phase 3 -- StateActor dispatch** (time-budgeted): business logic actors run within `ioTime * (100 - ioRatio) / ioRatio` nanoseconds (minimum 500us)

All actors on the same ActorThread are single-threaded with respect to each other -- no locks needed.

### Actor Separation: StateActor vs ChannelsActor

```
AbstractActor[M <: Call] extends FutureDispatcher
  +-- StateActor[M <: Call]      -- pure business logic, goes into actorQueue (Phase 3, time-budgeted)
  |     dispatchChannelStack() -> UnsupportedOperationException
  +-- ChannelsActor[M <: Call]   -- IO-capable, manages Channel instances, goes into channelsActorQueue (Phase 2, fully drained)
        dispatchChannelStack() -> processes ChannelStack
```

ChannelsActors are always fully drained in Phase 2 (no time budget), ensuring IO responsiveness is never starved. StateActors have a time budget in Phase 3, ensuring fair scheduling among many business actors.

---

## Stack Coroutine Mechanism

Messages execute via Stack objects (not direct method calls), implementing stackless coroutines -- resumable async execution.

### Stack types

- `AskStack[A]` / `NoticeStack[N]` -- handle Ask/Notice messages, provide `return(reply)` / `return()`
- `ChannelStack[T]` -- IO channel processing context, implements QueueMapEntity
- `BatchAskStack` / `BatchNoticeStack` -- batched variants

### State types (stack state helpers)

- `StartState` -- initial state, resumable=true
- `FutureState[R]` -- holds one `MessageFuture[R]`, waiting for a single actor reply
- `FuturesState[R]` -- holds `Seq[MessageFuture[R]]`, waiting for multiple replies
- `ChannelFutureState` -- holds one `ChannelFuture`, waiting for a channel response (e.g. HTTP reply after request, SQL result after query)

### Suspend/Resume Protocol

```
1. User code: address.ask(msg, state.future)        -- send message
   Kernel:    create Envelope -> sender.attachStack(askId, future)
              -> promise.stack = currentStack -> stack.addUncompletedPromise(promise)

2. User code: stack.suspend(state)                   -- suspend
   Kernel:    stack.nextState = state, stack is NOT recycled, persists with promise

3. [Reply arrives] Target actor calls stack.return(reply)
   -> sender.reply() -> house.putReply(envelope)

4. [Dispatch] ActorHouse processes reply mailbox FIRST (highest priority)
   -> FutureDispatcher.pop(replyId) -> finds promise
   -> promise.setSuccess(reply)
   -> handlePromiseCompleted(stack, promise)

5. [Resume] stack.moveCompletedPromise(promise)
   -> checks resumable or !hasUncompletedPromise
   -> re-dispatches stack -> user code resumes at match state.id
```

Stack is the unit of continuation, not the Actor. One actor can have multiple suspended stacks simultaneously (one per pending Ask). The FutureDispatcher hash table maps replyId to the corresponding promise, and each promise holds a reference to its owning Stack. This gives synchronous-looking async code without callbacks, future chains, or monadic composition.

---

## Channel Inflight Mechanism

`ChannelFuture` in Otavia is a higher-level abstraction than Netty's ChannelFuture. It does NOT represent completion of a single IO operation (write, connect, etc.). Instead, it represents **waiting for a response to a request sent through the channel** -- e.g. an HTTP response after sending a request, a database result after sending SQL. The `ChannelPromise` holds the `ask` (request) and `messageId`, and is matched against inbound responses by `messageId` correlation.

The inflight mechanism serves two roles depending on whether the actor acts as **client** or **server** on this channel:

- **Client role (Future path)**: actor sends requests through the channel and awaits responses. `ChannelPromise` tracks the request in `pendingFutures` -> `inflightFutures`. When the response arrives, matched by `messageId`, `promise.setSuccess(response)` wakes the suspended stack.
- **Server role (Stack path)**: network delivers requests to the actor. `ChannelStack` tracks the request in `pendingStacks` -> `inflightStacks`. Actor processes it and calls `stack.return(response)` to write the response back through the pipeline.

When an inbound message arrives via `onInboundMessage()`, the dispatch decision is: if a matching `inflightFuture` exists (by `messageId`), complete the Future (client response); otherwise, create a `ChannelStack` (server receives a new request).

Each `AbstractChannel` maintains four `QueueMap` instances (dual structure: power-of-2 hash table for O(1) lookup + FIFO queue for ordering):

```
Outbound (Actor -> Network):
  pendingFutures   -- ChannelPromises waiting to be encoded and buffered
  inflightFutures  -- ChannelPromises with requests encoded into outbound AdaptiveBuffer, awaiting response

Inbound (Network -> Actor):
  pendingStacks    -- ChannelStacks with decoded messages waiting for actor processing
  inflightStacks   -- ChannelStacks currently being processed by actor
```

### Client flow (actor sends request, awaits response)
`channel.ask(request)` -> creates ChannelPromise(ask=request, msgId) -> pendingFutures.append() -> Phase 2 processPendingFutures() -> moves to inflightFutures + encodes request through pipeline into outbound AdaptiveBuffer -> later flushed to network by IO handler

When response arrives: pipeline decodes -> onInboundMessage checks inflightFutures by messageId -> match found -> promise.setSuccess(response) -> wakes suspended Stack

### Server flow (network delivers request, actor responds)
Pipeline decodes inbound data -> onInboundMessage -> no matching inflightFuture -> creates ChannelStack -> pendingStacks -> inflightStacks -> actor receives ChannelStack -> processes -> stack.return(response) -> encodes response through pipeline into outbound AdaptiveBuffer -> flushed to network

### Pipelining support
`maxFutureInflight` controls how many requests can be in-flight without waiting for responses. When > 1, multiple requests are encoded and buffered before any response arrives. This enables high-performance pipelined protocols like Redis pipeline and HTTP/2 multiplexing without actor-level blocking.

### QueueMap design rationale
Inflight management needs both FIFO ordering (backpressure/flow control) and O(1) lookup by messageId (request-response correlation). QueueMap combines both in a single structure.

---

## Key Types

### Messages (`cc.otavia.core.message`)
- `Message` / `Call` / `Notice` / `Ask[R <: Reply]` / `Reply` -- type-safe message hierarchy
- `Envelope[T]` -- object-pooled message wrapper (internal to core)

### Actors (`cc.otavia.core.actor`)
- `Actor[M <: Call]` -- trait with lifecycle hooks (afterMount, beforeRestart, restart, afterRestart)
- `AbstractActor[M <: Call]` -- extends FutureDispatcher, implements stack coroutine mechanics
- `StateActor[M <: Call]` -- pure business logic
- `ChannelsActor[M <: Call]` -- IO-capable, manages Channel instances
- `SocketChannelsActor` / `DatagramChannelsActor` / `AcceptorActor` -- specialized IO actors

### Stacks (`cc.otavia.core.stack`)
- `AskStack[A]` / `NoticeStack[N]` / `ChannelStack[T]` -- resumable execution contexts
- `StackState` / `StackYield` -- suspend/resume state machine

### Channels (`cc.otavia.core.channel`)
- `ChannelPipeline` / `ChannelPipelineImpl` -- doubly-linked handler pipeline
- `ChannelHandlerContext` -- pipeline node, propagates inbound/outbound events
- `ChannelHandler` -- handler trait, `@Skip` masking, `@Sharable` sharing annotation
- `AbstractChannel` -- base channel with four-QueueMap inflight management
- `QueueMap` / `QueueMapEntity` -- O(1) lookup + FIFO ordering dual structure

### System (`cc.otavia.core.system`)
- `ActorSystem` / `ActorSystemImpl` -- top-level system, actor creation with pool sizing
- `ActorThread` -- IO thread + actor executor, three-phase event loop
- `ActorHouse` -- per-actor mailbox container, state machine (CREATED->MOUNTING->WAITING->READY->SCHEDULED->RUNNING), cached `_highPriority` flag for scheduling priority
- `HouseManager` -- priority queue scheduling (high/normal sub-queues), work stealing
- `PriorityHouseQueue` -- dual sub-queue (high drained before normal), SpinLock-based MPSC
- `Mailbox` -- five separate mailboxes per actor (reply, exception, ask, notice, event)

### Address (`cc.otavia.core.address`)
- `ActorAddress` -- routes directly to ActorHouse mailboxes
- `RobinAddress` -- round-robin with smart same-thread routing for data locality
- `ActorThreadAddress` -- routes events to ActorThread event queue

### Timer (`cc.otavia.core.timer`)
- `HashedWheelTimer` -- 100ms tick, 512 slots, dedicated thread
- `Timeout` / `TimeoutTrigger` -- FixTime, DelayTime, DelayPeriod, FirstTimePeriod modes

### Cache (`cc.otavia.core.cache`)
- `Poolable` -- base trait for object-pooled instances
- `ActorThreadIsolatedObjectPool` -- per-actor-thread pool, drops cross-thread recycles
- `ActorThreadLocal[V]` -- array-indexed O(1) thread-local via ActorThread.index

### IoC (`cc.otavia.core.ioc`)
- `BeanManager` -- ConcurrentHashMap storage, qualifier/supertype lookup
- `Module` / `AbstractModule` -- BeanDefinition sequences
- `@Component` -- auto-created actor annotation

## Package Layout

```
cc.otavia.core.actor          -- actor hierarchy and lifecycle
cc.otavia.core.address        -- address types and routing
cc.otavia.core.cache          -- object pooling and thread-locals
cc.otavia.core.channel        -- channel pipeline, handler, inflight, socket, udp
cc.otavia.core.ioc            -- IoC/DI container
cc.otavia.core.message        -- Notice, Ask, Reply message types
cc.otavia.core.reactor        -- file AIO reactor
cc.otavia.core.slf4a          -- SLF4A logging SPI
cc.otavia.core.stack          -- stack coroutines and state helpers
cc.otavia.core.system         -- ActorSystem, ActorThread, ActorHouse, HouseManager
cc.otavia.core.timer          -- HashedWheelTimer and timeout management
cc.otavia.core.transport      -- TransportFactory SPI, NIO implementation
cc.otavia.core.util           -- internal utilities
```

## Testing

19 test files under `test/src/`.
