---
sidebar_position: 6
title: Reactor Model
---

# Reactor Model

In otavia, each `ActorThread` owns an `IoHandler` (for NIO, this is a `NioHandler` wrapping a Java NIO `Selector`) and runs a three-phase loop: IO select/process, IO pipeline (ChannelsActors), and business logic (StateActors). The IO layer is transparent to most users — `ChannelsActor` encapsulates all IO interactions.

## IoHandler

`IoHandler` is the per-thread IO engine. Each `ActorThread` creates its own instance via `TransportFactory.openIoHandler()`. All channel IO operations (`register`, `bind`, `connect`, `read`, `flush`, `close`, etc.) are submitted to the thread's own `ioHandler`.

```
ActorThread
  └── ioHandler (IoHandler)
        ├── Selector (NIO Selector, one per thread)
        ├── run(ioCtx)       ── select + processSelectedKeys
        ├── register(channel)
        ├── bind(channel, local)
        ├── connect(channel, remote, local, fastOpen)
        ├── read(channel, plan)
        ├── flush(channel, payload)
        ├── close(channel)
        └── ...
```

## NioHandler

`NioHandler` is the concrete `IoHandler` implementation for Java NIO, wrapping a `java.nio.channels.Selector`.

### Selector Optimization

It uses `Unsafe` or reflection to replace the JDK's internal `selectedKeys` and `publicSelectedKeys` fields in `SelectorImpl` with `SelectedSelectionKeySet` — a custom `Array[SelectionKey]`-backed set that avoids iterator allocation overhead. This is the same optimization Netty uses.

### Core Methods

**`run(context)`**: Main entry point — calls `select(context)` then `processSelectedKeys()`.

**`select(context)`**: If `context.canNotBlock`, calls `selector.selectNow()` (non-blocking); otherwise calls `selector.select()` (blocking).

**`processSelectedKeys()`**: Iterates the optimized key array, calling `processSelectedKey(key)` for each.

**`processSelectedKey(key)`**: Extracts `NioUnsafeChannel` from `key.attachment()` and calls `processor.handle(key)`.

### Epoll Bug Workaround

When the NIO selector returns empty results 512 consecutive times (configurable via `io.otavia.selectorAutoRebuildThreshold`), `NioHandler` rebuilds the Selector to work around the JDK epoll 100% CPU bug.

## IO Within the ActorThread Loop

The IO phase is Phase 1 of the `ActorThread` three-phase loop. During this phase, `ioHandler.run(ioCtx)` performs NIO select and processes ready keys. Raw bytes are read from the socket and processed through the channel pipeline on the current thread. Decoded messages then enter the Actor mailbox (via the Inflight system), and the ChannelsActor processes them in Phase 2.

The `ioRatio` configuration (default 50) determines how much time is allocated to IO versus business logic within each loop iteration.

## SPI Mechanism

The IO transport layer is implemented via SPI (Service Provider Interface). `TransportFactory` creates the concrete `IoHandler`, `ChannelFactory`, and `Reactor` implementations.

- Default: `NIOTransportFactory` → NIO-based transport
- Pluggable: Replace with epoll/io_uring implementations by adding the JAR to the CLASSPATH

The [native-transport](https://github.com/otavia-projects/native-transport) project aims to provide epoll- and io_uring-based implementations.

## NIO Transport Implementations

### NioUnsafeSocketChannel (TCP)

- **Read**: Allocates page buffer, reads from `SocketChannel`, calls `channel.handleChannelReadBuffer()` directly on the current thread
- **Write**: Writes `RecyclablePageBuffer` chain to `SocketChannel`, enables `OP_WRITE` on partial write
- **Connect**: Non-blocking connect, sets `OP_CONNECT` if not immediately ready

### NioUnsafeServerSocketChannel (TCP Server)

- **Accept**: Calls `javaChannel.accept()`, creates new `NioSocketChannel`, sends `AcceptedEvent` to the actor mailbox

### NioUnsafeDatagramChannel (UDP)

- **Read**: Calls `ch.receive(byteBuffer)`, sends `ReadBuffer` event to actor mailbox
- **Write**: Writes buffer directly to `DatagramChannel`
