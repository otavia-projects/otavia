---
sidebar_position: 4
title: Channel Pipeline
---

# Channel Pipeline

The `ChannelPipeline` is the core processing chain for channel data in otavia. It is fundamentally similar to Netty's ChannelPipeline but with important differences in how it integrates with the Actor model.

![](/img/pipeline.drawio.svg)

## Structure

`ChannelPipelineImpl` maintains a doubly-linked list of `ChannelHandlerContextImpl` nodes with two sentinel nodes:

```
HeadHandler ⇄ Handler[0] ⇄ Handler[1] ⇄ ... ⇄ Handler[N] ⇄ TailHandler
```

- **HeadHandler**: The head sentinel. Bridges outbound operations to the channel's transport methods.
- **TailHandler**: The tail sentinel. Bridges inbound events to the channel's `Inflight` system.
- **User handlers**: Inserted between Head and Tail, process data in sequence.

## Event Propagation

### Inbound (Head → Tail)

Inbound events flow from `HeadHandler` toward `TailHandler`:

1. `ActorThread`'s `IoHandler` processes an IO ready event → raw bytes are read and enter the pipeline
2. Pipeline processes the data through the handler chain
3. Channel calls `pipeline.fireChannelRead(data)` → HeadHandler
4. Each handler processes the data and calls `ctx.fireChannelRead(data)` to pass to the next
5. `TailHandler.channelRead()` → `channel.onInboundMessage(msg)` → enters the Inflight system

Common inbound events: `channelRead`, `channelReadComplete`, `channelRegistered`, `channelActive`, `channelInactive`, `channelExceptionCaught`

### Outbound (Tail → Head)

Outbound events flow from the last handler toward `HeadHandler`:

1. Actor calls `channel.write(msg)` → `pipeline.write(msg)`
2. Pipeline scans backward from the last handler
3. Each handler processes and calls `ctx.write(msg)` to pass to the previous
4. `HeadHandler.write()` → `channel.writeTransport(msg)` → writes to `AdaptiveBuffer`
5. `HeadHandler.flush()` → `channel.flushTransport()` → sends to IO handler

Common outbound events: `write`, `flush`, `bind`, `connect`, `register`, `close`, `read`

## HeadHandler

The head sentinel bridges the pipeline to the channel's transport methods:

| Method | Target |
|--------|--------|
| `bind` | `channel.bindTransport(local, promise)` |
| `connect` | `channel.connectTransport(remote, local, promise)` |
| `register` | `channel.registerTransport(promise)` |
| `read` | `channel.readTransport()` → `ioHandler.read(channel, plan)` |
| `write` | `channel.writeTransport(msg)` → writes to `channelOutboundAdaptiveBuffer` |
| `flush` | `channel.flushTransport()` → drains outbound queue to IO handler |
| `close` | `channel.closeTransport(promise)` |

## TailHandler

The tail sentinel bridges the pipeline to the channel's `Inflight` system:

| Method | Behavior |
|--------|----------|
| `channelRead(msg)` | Calls `channel.onInboundMessage(msg, false)` — routes decoded message to Inflight |
| `channelExceptionCaught(cause, id)` | Calls `channel.onInboundMessage(cause, true, id)` — routes exception to Inflight |
| `channelReadComplete` | No-op (swallowed) |
| `channelActive/Inactive` | No-op (swallowed) |

This is the critical bridge: when data arrives through the pipeline, the TailHandler passes it to `AbstractChannel.onInboundMessage`, which routes it to the Inflight/Stack system for Actor processing.

## executionMask Optimization

Each `ChannelHandler` has an `executionMask` — a 27-bit field where each bit corresponds to a handler method (computed via `ChannelHandlerMask.mask(handlerClass)` using the `@Skip` annotation). The pipeline maintains a global `executionMask` which is the bitwise OR of all handler masks.

Before scanning for a handler:
- `findContextInbound(mask)`: Check `pipeline.executionMask & mask`. If zero, no handler handles this event — skip directly to `TailHandler`.
- `findContextOutbound(mask)`: Same check, skip to `HeadHandler` if no match.

This avoids linear scans when most handlers use default `@Skip` implementations.

## AdaptiveBuffer Integration

When handlers require buffers (codec handlers with `hasInboundAdaptive`/`hasOutboundAdaptive`), the pipeline automatically allocates `AdaptiveBuffer` instances:

- The first handler needing an outbound buffer gets the channel-level `channelOutboundAdaptiveBuffer` (shared)
- Subsequent handlers get their own heap-allocated `AdaptiveBuffer`
- Inbound buffers: first handler gets a new heap buffer; the head context holds `channelInboundAdaptiveBuffer`

This creates a zero-copy buffer chain between codec handlers.

## Adding Handlers

```scala
// During channel initialization
override protected def initChannel(channel: Channel): Unit = {
  channel.pipeline.addLast(new MyDecoder())
  channel.pipeline.addLast(new MyEncoder())
  channel.pipeline.addLast(new MyBusinessHandler())
}
```

Handler order matters — inbound data flows through handlers in insertion order (first added = first to process).

## Key Difference from Netty

In Netty, all business logic must reside in `ChannelHandler`s within the pipeline. If an inbound event reaches `TailHandler` unprocessed, it's ignored.

In otavia, the `TailHandler` routes unprocessed inbound messages to the channel's `Inflight` system, which delivers them to the `ChannelsActor` as `ChannelStack`s. This means the pipeline's role is focused on **byte-level processing** (encoding, decoding, TLS, compression), while business logic is handled by the Actor through the Stack mechanism.
