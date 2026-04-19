---
sidebar_position: 5
title: IO Model
---

# IO Model

The `otavia` IO model is a layered, actor-integrated IO framework inspired by Netty but re-architected around the Actor model. Each `ActorThread` owns its own `IoHandler` (with a dedicated NIO `Selector`) and runs both IO and actor logic in a time-sliced loop.

![](/img/architecture_of_channel.drawio.svg)

## Architecture Layers

```
Java NIO (Selector / SelectionKey)
    │
NioHandler (IoHandler ── owned by each ActorThread)
    │
Channel Transport (AbstractNioUnsafeChannel, NioUnsafeSocketChannel, etc.)
    │
Channel Pipeline (ChannelPipelineImpl ── doubly-linked handler chain)
    │
AbstractChannel (inflight management + barrier mechanism)
    │
ChannelsActor (processes decoded messages from Inflight system)
```

## Channel Inflight

`AbstractChannel` manages message concurrency through four `QueueMap` instances:

```scala
// Outbound (Actor → Channel → Network)
inflightFutures: QueueMap[ChannelPromise]  // Currently being processed by IO
pendingFutures:  QueueMap[ChannelPromise]  // Waiting to be sent

// Inbound (Network → Channel → Actor)
inflightStacks: QueueMap[ChannelStack[?]]  // Currently being processed by Actor
pendingStacks:  QueueMap[ChannelStack[?]]  // Waiting for Actor processing
```

`QueueMap` is a custom data structure combining a hash map (O(1) lookup by entity ID for response correlation) with a doubly-linked queue (FIFO ordering for sequential processing).

![](/img/channel_inflight.drawio.svg)

### Barrier Flow Control

Two barrier functions control message flow:

- **`futureBarrier: AnyRef => Boolean`** (default `_ => false`): Controls **outbound** flow. When a barrier message is detected, only one future can be in flight at a time.
- **`stackBarrier: AnyRef => Boolean`** (default `_ => true`): Controls **inbound** flow. When true (default), messages are processed one at a time.

Configurable via `ChannelOption`:

| Option | Description | Default |
|--------|-------------|---------|
| `CHANNEL_FUTURE_BARRIER` | Outbound barrier predicate | `_ => false` |
| `CHANNEL_STACK_BARRIER` | Inbound barrier predicate | `_ => true` |
| `CHANNEL_MAX_FUTURE_INFLIGHT` | Max concurrent outbound futures | 1 |
| `CHANNEL_MAX_STACK_INFLIGHT` | Max concurrent inbound stacks | 1 |
| `CHANNEL_STACK_HEAD_OF_LINE` | Head-of-line blocking for stacks | false |

## Channel Behavioral Abstractions

### ChannelFuture

`ChannelFuture` represents the Actor sending a request to a Channel and expecting a reply. It is managed by `StackState` and integrates with the Stack execution model. Usage is through `ChannelAddress`:

```scala
// Inside ChannelsActor, using ChannelFutureState:
val state = ChannelFutureState()
channel.ask(myRequest, state.future)
stack.suspend(state)
```

### ChannelStack

`ChannelStack` represents the Channel sending a decoded message to the Actor for business processing. When the TailHandler receives an inbound message, it creates a `ChannelStack` and routes it through the Inflight system to the Actor.

**Note**: Unlike Netty's `ChannelFuture` (which tracks the result of a single outbound call), `otavia`'s `ChannelFuture` is a higher-level abstraction representing the expected data response to a network request.

## Complete TCP Read Lifecycle

```
1. Actor calls channel.read() → pipeline.read() → HeadHandler.read()
   → channel.readTransport() → ioHandler.read(channel, plan)

2. NioHandler processes selected key with OP_READ
   → NioUnsafeSocketChannel.handle(key) → readNow() → readLoop()

3. doReadNow0(): page.transferFrom(ch) reads from socket
   → channel.handleChannelReadBuffer(page)

4. Data enters channelInboundAdaptiveBuffer → pipeline.fireChannelRead(buffer)

5. Pipeline processes: Decoder → ... → TailHandler
   → TailHandler.channelRead → channel.onInboundMessage(msg)

6. onInboundMessage: creates ChannelStack → pendingStacks
   → processPendingStacks → executor.receiveChannelMessage(stack)  [enters actor mailbox]

7. Actor thread processes ChannelsActor → dispatchChannelStack → resumeChannelStack
   → User code processes the decoded message
```

**Key insight**: Raw bytes are processed through the pipeline during Phase 1 (IO) of the `ActorThread` loop. Decoded messages enter the Actor mailbox via the Inflight system and are processed in Phase 2 (IO Pipeline).

## Complete TCP Write Lifecycle

```
1. Actor calls channel.ask(value, future)
   → Creates ChannelPromise → pendingFutures → schedule processing

2. ActorHouse.run() → processPendingFutures()
   → pendingFutures.pop() → inflightFutures.append(promise)
   → pipeline.writeAndFlush(msg)

3. Pipeline outbound: Encoder → ... → HeadHandler.write()
   → channel.writeTransport(msg) → writes to channelOutboundAdaptiveBuffer

4. HeadHandler.flush() → channel.flushTransport()
   → ioHandler.flush(channel, payload) → NioUnsafeSocketChannel.unsafeFlush()
   → Writes to SocketChannel, enables OP_WRITE if partial

5. Response arrives: Read → Pipeline → TailHandler → onInboundMessage
   → Resolves inflight future: promise.setSuccess(msg)
   → Actor's attached future completes → Stack resumes
```

## Complete TCP Accept Lifecycle

```
1. Server channel registered + bound → OP_ACCEPT interest set

2. NioHandler.run() → OP_ACCEPT ready → NioUnsafeServerSocketChannel.handle()
   → doReadNow() → javaChannel.accept() → new SocketChannel

3. Creates NioSocketChannel → executorAddress.inform(AcceptedEvent)
   → Event enters ChannelsActor mailbox

4. ChannelsActor receives AcceptedEvent → pipeline.fireChannelRead(accepted)
   → User handler processes the new channel (registers with the thread's ioHandler)
```

## Channel State

Channel state is stored in a single `Long` using bit compression. Twenty-two lifecycle booleans (`created`, `registered`, `bound`, `connected`, etc.) and configuration booleans (`autoRead`, `writable`, etc.) are packed into one 64-bit word.

![](/img/state_of_channel.drawio.svg)
