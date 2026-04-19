---
title: Reactor 模型
---

# Reactor 模型

在 otavia 中，每个 `ActorThread` 拥有自己的 `IoHandler`（NIO 模式下为封装 Java NIO `Selector` 的 `NioHandler`），运行三阶段循环：IO select/处理、IO Pipeline（ChannelsActor）、业务逻辑（StateActor）。IO 层对大多数用户是透明的 — `ChannelsActor` 封装了所有 IO 交互。

## IoHandler

`IoHandler` 是每线程 IO 引擎。每个 `ActorThread` 通过 `TransportFactory.openIoHandler()` 创建自己的实例。所有 channel IO 操作（`register`、`bind`、`connect`、`read`、`flush`、`close` 等）提交给线程自身的 `ioHandler`。

```
ActorThread
  └── ioHandler (IoHandler)
        ├── Selector (NIO Selector，每线程一个)
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

`NioHandler` 是 Java NIO 的具体 `IoHandler` 实现，封装了 `java.nio.channels.Selector`。

### Selector 优化

使用 `Unsafe` 或反射替换 JDK 内部 `SelectorImpl` 的 `selectedKeys` 和 `publicSelectedKeys` 字段为 `SelectedSelectionKeySet` — 一个基于 `Array[SelectionKey]` 的自定义集合，避免迭代器分配开销。与 Netty 使用相同的优化。

### 核心方法

**`run(context)`**：主入口 — 调用 `select(context)` 然后 `processSelectedKeys()`。

**`select(context)`**：`canNotBlock` 时调用 `selector.selectNow()`（非阻塞），否则调用 `selector.select()`（阻塞）。

**`processSelectedKeys()`**：迭代优化的 key 数组，对每个调用 `processSelectedKey(key)`。

**`processSelectedKey(key)`**：从 `key.attachment()` 取出 `NioUnsafeChannel`，调用 `processor.handle(key)`。

### Epoll Bug 规避

当 NIO Selector 连续 512 次返回空结果时（可通过 `io.otavia.selectorAutoRebuildThreshold` 配置），`NioHandler` 重建 Selector 以规避 JDK epoll 100% CPU Bug。

## IO 与 ActorThread 循环的集成

IO 阶段是 `ActorThread` 三阶段循环的第一阶段。在此阶段，`ioHandler.run(ioCtx)` 执行 NIO select 并处理就绪 key。原始字节从 socket 读取后，在当前线程上通过 channel pipeline 处理。解码后的消息进入 Actor 邮箱（通过 Inflight 系统），ChannelsActor 在第二阶段处理它们。

`ioRatio` 配置（默认 50）决定每次循环迭代中 IO 与业务逻辑的时间分配比例。

## SPI 机制

IO 传输层通过 SPI（Service Provider Interface）实现。`TransportFactory` 创建具体的 `IoHandler`、`ChannelFactory` 和 `Reactor` 实现。

- 默认：`NIOTransportFactory` → 基于 NIO 的传输
- 可插拔：通过将 JAR 加入 CLASSPATH 替换为 epoll/io_uring 实现

[native-transport](https://github.com/otavia-projects/native-transport) 项目的目标是提供基于 epoll 和 io_uring 的实现。

## NIO Transport 实现

### NioUnsafeSocketChannel（TCP）

- **读**：分配 page buffer，从 `SocketChannel` 读取，直接在当前线程上调用 `channel.handleChannelReadBuffer()`
- **写**：将 `RecyclablePageBuffer` 链写入 `SocketChannel`，部分写入时启用 `OP_WRITE`
- **连接**：非阻塞 connect，未立即完成时设置 `OP_CONNECT`

### NioUnsafeServerSocketChannel（TCP Server）

- **Accept**：调用 `javaChannel.accept()`，创建新 `NioSocketChannel`，发送 `AcceptedEvent` 到 actor 邮箱

### NioUnsafeDatagramChannel（UDP）

- **读**：调用 `ch.receive(byteBuffer)`，发送 `ReadBuffer` 事件到 actor 邮箱
- **写**：直接将 buffer 写入 `DatagramChannel`
