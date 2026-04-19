---
title: Actor 模型
---

# Actor 模型

## Actor 层次

Actor 类型层次有三个主要分支：

```
Actor[+M <: Call]                    (根 trait)
  └── AbstractActor[M <: Call]       (引擎：FutureDispatcher + 消息分发)
        ├── StateActor[M <: Call]    (纯业务逻辑，无 IO)
        │     └── MainActor          (入口 Actor，挂载后自动发送 Args notice)
        └── ChannelsActor[M <: Call] (IO actor，管理 Channel 实例)
              ├── AcceptorActor[W]   (TCP 服务端 acceptor)
              ├── AcceptedWorkerActor[M] (处理已接受的连接)
              ├── SocketChannelsActor[M]  (TCP 客户端)
              └── DatagramChannelsActor[M] (UDP)
```

![](/img/actor_type.drawio.svg)

## AbstractActor 引擎

`AbstractActor` 是将消息桥接到 Stack 执行模型的核心引擎。它扩展了 `FutureDispatcher`（一个用于追踪未完成 Promise 的自定义哈希表）。

### 接收消息

当消息到达时，`ActorHouse.run()` 将其分发给 `AbstractActor`：

**Notice**（`receiveNotice`）：从 Envelope 中提取 Notice，回收 Envelope，从对象池分配 `NoticeStack`，调用 `dispatchNoticeStack`。

**Ask**（`receiveAsk`）：提取 Ask，从池获取 `AskStack`，从 Envelope 中存储 sender 地址和 askId（在回收 Envelope 之前），然后调用 `dispatchAskStack`。

**Reply**（`receiveReply`）：提取 Reply 和 replyId，在 `FutureDispatcher` 哈希表中查找对应的 `MessagePromise`，弹出后调用 `handlePromiseCompleted` 恢复挂起的 Stack。

**Event**（`receiveEvent`）：按事件类型模式匹配：
- `AskTimeoutEvent` → 弹出 promise，设置为 `TimeoutReply` 失败
- `TimeoutEvent` → 调用用户定义的 `handleActorTimeout`
- `ChannelTimeoutEvent` → 委托给 channel
- `ReactorEvent` → 委托给 `ChannelsActor`

### 邮箱调度顺序

`ActorHouse` 有 5 个独立的邮箱，严格按照优先级顺序处理：

1. **回复**（replyMailbox）— 始终最先排空，恢复挂起的 Stack
2. **异常**（exceptionMailbox）— 异常回复，也解除 barrier
3. **Ask**（askMailbox）— 仅当未处于 barrier 模式时
4. **Notice**（noticeMailbox）— 仅当未处于 barrier 模式时
5. **事件**（eventMailbox）— 无论 barrier 状态都处理

对于 `ChannelsActor`，channel 的待处理 future 和后续任务在事件之后处理。

## ActorHouse 状态机

每个 Actor 包装在一个 `ActorHouse` 中，管理其生命周期：

```
CREATED(0) → MOUNTING(1) → WAITING(2) → READY(3) → SCHEDULED(4) → RUNNING(5)
```

- **CREATED → MOUNTING**：Actor 首次创建
- **MOUNTING → WAITING**：Actor 挂载完成，设置运行时属性
- **WAITING → READY**：消息/事件到达（CAS 转换）
- **READY → SCHEDULED**：从调度队列中取出
- **SCHEDULED → RUNNING**：即将执行

`run()` 完成后 `completeRunning()` 转换到：
- **READY**（重新入队）— 如果还有更多消息
- **WAITING** — 如果为空（带竞态条件重检查）

### 高优先级

Actor 在以下任一条件下变为高优先级：
- 回复邮箱中消息数超过 2 条——每条 reply 对应一个可完成的 future，可能解除一个挂起栈的阻塞并释放池化资源
- 事件邮箱中消息数超过 4 条——系统事件（定时器到期、channel 生命周期）需要及时处理
- `pendingPromiseCount == 0`——该 actor 当前没有等待其他 actor 回复的 ask，即没有 stack 因为等待下游而挂起。调度此 actor 不会遇到下游阻塞，CPU 时间直接转化为业务流推进

高优先级标志（`_highPriority`）缓存在 `ActorHouse` 上，而非每次访问时重新计算。任何线程在 `putReply`/`putEvent` 中当邮箱超过阈值时会主动设为 `true`，owning 线程在每次 dispatch 结束后的 `completeRunning` 中完整重算三个条件。优先级在入队时确定，不做中途提升。高优先级 Actor 会被放入独立的子队列，始终在普通优先级 actor 之前处理。

## Barrier 机制

当一个 Ask 消息被标记为 barrier（`isBarrierCall`）时，Actor 进入 barrier 模式。在收到回复或事件解除 barrier 之前，不会处理额外的 ask 或 notice。这保证了操作的原子性顺序。

## 批量处理

当 `batchable == true` 时，`ActorHouse` 会使用 `batchAskFilter`/`batchNoticeFilter` 过滤消息，将通过过滤的消息收集到缓冲区，作为单个 `BatchAskStack`/`BatchNoticeStack` 分发。未通过过滤的消息单独分发。

## StateActor

`StateActor[M <: Call]` 是纯业务逻辑 actor。不能管理 Channel（对 Channel 进行分发操作时会抛出 `UnsupportedOperationException`）。用于状态管理、消息处理和定时器交互。

## ChannelsActor

`ChannelsActor[M <: Call]` 在 `StateActor` 基础上增加了 IO 能力。通过 `createChannelAndInit()` 创建 channel。接收线程 `IoHandler` 产生的 `ReactorEvent` 并分发给相应的 channel。

### AcceptorActor

服务端 actor，用于接受 TCP 连接。挂载时：
1. 创建一组 `AcceptedWorkerActor` 实例
2. 绑定 `ServerSocketChannel`
3. 当连接被接受时，包装为 `AcceptedChannel` 消息发送给某个 worker

### AcceptedWorkerActor

处理来自 `AcceptorActor` 的已接受连接：
1. 接收 `AcceptedChannel` Ask
2. 挂载 channel 并初始化 pipeline
3. 将 channel 注册到线程的 `IoHandler`

### SocketChannelsActor

管理 TCP 客户端 channel。用于出站 TCP 连接。

### DatagramChannelsActor

管理 UDP channel。

## Actor 生命周期

整个生命周期由运行时管理：

![](/img/actor_life_cycle.drawio.svg)

### 生命周期钩子

- `afterMount()`：Actor 挂载到 ActorSystem 后调用。此时运行时属性（logger、context、address）可用。
- `beforeRestart()`：重启前调用。
- `restart()`：重启方法本身。
- `afterRestart()`：重启后调用。

### AutoCleanable

如果你的 Actor 持有不安全资源，继承 `AutoCleanable` 并实现 `cleaner` 方法。cleaner 创建一个 `ActorCleaner`，使用 JVM 幽灵引用检测 Actor 何时被垃圾回收并清理资源。

**警告**：`ActorCleaner` 不能持有 Actor 对象或其 Address 的引用，否则 Actor 将永远不会被垃圾回收。
