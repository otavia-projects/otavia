---
title: 消息模型
---

# 消息模型

消息是 Actor 用来通信的特殊对象，建议使用 `case class` 来定义。

## 消息类型层次

```
Message (sealed trait)
  ├── Call (sealed trait)          — 接收时产生 Stack
  │     ├── Notice extends Call   — 即发即弃，无需回复
  │     └── Ask[R <: Reply]       — 期望类型化回复
  └── Reply (trait)                — Ask 的响应
        ├── TimeoutReply           — 系统超时响应（单例）
        └── ExceptionMessage       — 将 Throwable 包装为 Reply
```

![](/img/message_types.drawio.svg)

### Call

`Call` 代表请求消息，类似于方法调用。它有两个子类型：

- **`Notice`**：即发即弃消息，类似于返回值为 `Unit` 的方法。不需要回复。
- **`Ask[R <: Reply]`**：请求-响应消息，类型参数 `R` 指定期望的回复类型。

### Reply

`Reply` 是 `Ask` 的响应。两个特殊的系统回复：

- **`TimeoutReply`**：当 Ask 超时时生成的单例。
- **`ExceptionMessage`**：包装 `Throwable`，将异常作为回复传递。

## 编译时类型安全

`otavia` 通过 `Actor` 和 `Address` 的类型参数在编译时强制消息类型安全：

```scala
trait Actor[+M <: Call]
trait Address[-M <: Call]
```

Actor 通过 `M` 声明可接收的消息类型。对应的 `Address` 具有逆变的类型参数，只能向地址发送 `M` 类型的消息。

`ReplyOf[A <: Ask[?]]` 匹配类型从 `Ask[R]` 中提取回复类型 `R`，确保 `AskStack.return()` 只接受正确的回复类型。

```scala
// 示例：编译时类型安全的消息
case class Greet(name: String) extends Notice
case class AskName(id: Int) extends Ask[NameReply]
case class NameReply(name: String) extends Reply

// 接收两种消息类型的 Actor
class MyActor extends StateActor[Greet | AskName] {
  override protected def resumeNotice(stack: NoticeStack[Greet]): StackYield = ???
  override protected def resumeAsk(stack: AskStack[AskName]): StackYield = ???
}
```

### 双类型消息

一个消息可以同时继承 `Notice` 和 `Ask`。如何处理取决于发送方式：

```scala
case class DataUpdate(data: String) extends Notice, Ask[UnitReply]

// 作为 Notice 发送（不需要回复）：
address.notice(DataUpdate("hello"))

// 作为 Ask 发送（需要回复）：
address.ask(DataUpdate("hello"), state.future)
```

![](/img/message_model.drawio.svg)

## Envelope

每条在 Actor 之间发送的消息都包装在 `Envelope[M <: Message]` 中：

| 字段 | 说明 |
|------|------|
| `address` | 发送者 Address |
| `mid` | 唯一消息 ID（每个 Actor 单调递增） |
| `msg` | 实际消息体 |
| `rid` | 单个 reply ID（用于单条回复） |
| `rids` | reply ID 数组（用于批量回复） |

Envelope 通过 `ActorThreadIsolatedObjectPool` 池化，在接收者提取消息数据后立即回收。这消除了高频消息传递带来的 GC 压力。

## Event 模型

Event 与消息分离，代表 Actor 与运行时系统之间的交互。其类型是固定的，用户不能自定义。

### TimerEvent

由 `Timer` 组件生成：

- `TimeoutEvent` — 用户注册的超时（直接由开发者代码处理）
- `ChannelTimeoutEvent` — Channel 特定超时
- `AskTimeoutEvent` — Ask 超时（携带 askId 用于 FutureDispatcher 查找）
- `ResourceTimeoutEvent` — 缓存资源超时

### ReactorEvent

由每个 `ActorThread` 内的 `IoHandler` 为 IO 事件生成。被 `ChannelsActor` 封装：

- `RegisterReply`、`DeregisterReply` — Channel 注册结果
- `BindReply`、`ConnectReply`、`DisconnectReply` — 网络操作结果
- `OpenReply`、`ShutdownReply` — 文件/关闭结果
- `ChannelClose` — Channel 已关闭
- `ReadBuffer` — UDP 读取数据（携带发送者地址）
- `AcceptedEvent` — 新 TCP 连接被接受
- `ReadCompletedEvent` — 读循环完成
- `ReadEvent` — 读取错误通知

Event 通过 `EventableAddress.inform()` 传递到 Actor 的 `eventMailbox`。
