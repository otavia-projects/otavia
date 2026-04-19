---
title: Address 模型
---

# Address 模型

Actor 之间相互隔离，只能通过 `Address` 实例通信。Address 模型提供编译时类型安全和灵活的路由策略。

## Address Trait

`Address[-M <: Call]` 是根 trait（对 `M` 逆变）。它提供核心消息传递 API：

```scala
trait Address[-M <: Call] {
  def notice(notice: M & Notice): Unit
  def ask(ask: M & Ask[? <: Reply]): FutureState[? <: Reply]
  def ask(ask: M & Ask[? <: Reply], future: Future[? <: Reply]): Unit
  def reply(reply: Reply, replyId: Long, sender: AbstractActor[?]): Unit
}
```

逆变性意味着 `Address[Call]` 可以发送任何消息类型，而 `Address[MyNotice]` 只能发送 `MyNotice` 消息。

## PhysicalAddress

`PhysicalAddress[M]` 是绑定到单个 `ActorHouse` 的具体地址。它是所有直接路由消息到特定 actor 邮箱的地址类型的基类。

### 消息路由

通过 `PhysicalAddress` 发送消息时：

**Ask**（`ask` 方法）：
1. `packaging(ask, sender)` — 从池创建 `Envelope`，设置 sender/messageId/content
2. `sender.attachStack(envelope.messageId, future)` — 将 promise 关联到 sender 的当前 Stack 和 FutureDispatcher
3. `house.putAsk(envelope)` — 放入目标 actor 的 `askMailbox`

**Notice**（`notice` 方法）：
1. 创建 Envelope（无需 sender）
2. `house.putNotice(envelope)` — 放入目标 actor 的 `noticeMailbox`

**Reply**（`reply` 方法）：
1. 创建带 `replyId`（单个）或 `replyIds`（数组）的 Envelope
2. `house.putReply(envelope)` — 放入 sender actor 的 `replyMailbox`

### 超时支持

发送带超时的 Ask 时：
```scala
address.ask(myAsk, future, timeout)
```
Ask 发送后，通过 `sender.timer.registerAskTimeout(...)` 注册定时器任务。如果超时前未收到回复，将传递 `TimeoutReply`。

## ActorAddress

`ActorAddress[M]` 是最简单的地址类型 — 扩展 `PhysicalAddress` 的 final class。每个 `ActorHouse` 为其 actor 创建恰好一个 `ActorAddress`。

```scala
final class ActorAddress[M <: Call](house: ActorHouse) extends PhysicalAddress[M]
```

## RobinAddress

`RobinAddress[M]` 提供一组 `ActorAddress` 实例上的轮询负载均衡。当 `ActorSystem.buildActor` 以 `num > 1` 调用时创建。

### 路由策略

**Notice 路由**：
简单轮询：`noticeCursor % underlying.length`

**Ask 路由** — 两种模式：

1. **线程亲和（Load Balance 模式）**：当 `sender.isLoadBalance && isLB` 为 true 时，路由到 `underlying(sender.mountedThreadId)` — 发送到**同一线程**的 worker，避免跨线程邮箱写入。这是最优路径。

2. **轮询**：否则使用 `askCursor % underlying.length`

### RobinAddress 创建时机

- `num == pool.size`：每个线程一个 actor，全部标记 `isLoadBalance = true`。带 `isLB = true` 的 RobinAddress 启用线程亲和路由。
- `num > 1`（其他）：跨选定线程分配 actor，使用简单轮询。

## EventableAddress

`EventableAddress` 是带有 `inform(event)` 方法的 trait，用于定时器/事件传递。`PhysicalAddress` 覆盖此方法将事件放入 house 的 `eventMailbox`。

还有 `ActorThreadAddress`，它将事件路由到线程的 `eventQueue`（`ConcurrentLinkedQueue`）— 用于需要线程级处理的 `ResourceTimeoutEvent`。

## Address 与类型安全

类型系统确保你不能向 Actor 发送错误的消息类型：

```scala
case class Ping extends Notice
case class Pong extends Notice
case class AskData extends Ask[DataReply]
case class DataReply(value: String) extends Reply

class PingActor extends StateActor[Ping | AskData]

// 创建 actor 返回类型化的 Address：
val address: Address[Ping | AskData] = system.buildActor[PingActor](ActorFactory[PingActor])

// 编译时安全：
address.notice(Ping())        // OK
address.ask(AskData())        // OK

// 编译时错误：
// address.notice(Pong())     // 错误：Pong 不是 Ping | AskData
```
