---
title: 线程模型
---

# 线程模型

otavia 采用 `ActorThread`-per-CPU 模型。每个 `ActorThread` 是独立的 OS 线程，运行自己的事件循环，拥有独立的 Buffer 池和调度队列。

## ActorThread 主循环

每个 `ActorThread` 运行三阶段循环：

```
while (!confirmShutdown()) {
    Phase 1: IO              ── ioHandler.run(ioCtx)
    Phase 2: IO Pipeline     ── manager.runChannelsActors()
                               ── runThreadEvent()
                               ── stopActors()
    Phase 3: Business        ── manager.runStateActors(deadline)
}
```

### Phase 1: IO

执行 NIO select 并处理就绪 key。`ioCtx.canBlock` 仅在无待运行 actor、无待处理事件、无 phantom ref 需清理时为 true。

每次 IO 循环后，`selectCnt` 计数器追踪连续空唤醒。达到 512 次（可配置）时，重建 Selector 以规避 JDK epoll 100% CPU Bug。

### Phase 2: IO Pipeline

无时间预算，运行到完成：

1. **`manager.runChannelsActors()`**：完全排空 `channelsActorQueue` 和 `mountingQueue`
2. **`runThreadEvent()`**：从 `ConcurrentLinkedQueue[Event]` 轮询定时器/资源超时事件
3. **`stopActors()`**：排空 `ReferenceQueue`，清理被 GC 的 phantom-ref 引用的 actor

### Phase 3: Business

StateActor 在纳秒级 `deadline` 约束下运行：

- `ioRatio == 100`：无 deadline（无限）
- 无 IO 事件：`deadline = now + 500μs`（最低保障）
- 默认（`ioRatio == 50`）：`actorTime = ioTime * (100 - ioRatio) / ioRatio`

每 16 个 actor 检查一次 deadline（`count & 0xF == 0`），分摊 `System.nanoTime()` 系统调用开销。

### 线程通知

当消息到达不同线程上的 actor 时，`notifyThread()` 调用 `ioHandler.wakeup()` 中断阻塞的 `select()`。

## HouseManager

每个 `ActorThread` 有一个 `HouseManager`，管理三个调度队列：

| 队列 | 类型 | 用途 | 时间预算 |
|------|------|------|----------|
| `mountingQueue` | FIFOHouseQueue | 待挂载的 Actor | 无限制 |
| `channelsActorQueue` | PriorityHouseQueue | IO 相关 Actor | 无限制 |
| `actorQueue` | PriorityHouseQueue | 业务逻辑 StateActor | 受 deadline 约束 |

### 路由

当 ActorHouse 转换为 READY 时，`HouseManager.ready()` 路由：
- `STATE_ACTOR` → `actorQueue`
- `CHANNELS_ACTOR` / `SERVER_CHANNELS_ACTOR` → `channelsActorQueue`

## PriorityHouseQueue

双队列设计，普通和高优先级各有独立的链表，各自有 `SpinLock` 保护：

- **出队**：始终先检查高优先级队列（严格优先）
- **SpinLock**：基于 `AtomicReference[Thread]`，CAS 加锁，`lazySet(null)` 解锁

### SpinLock 实现

```scala
class SpinLock extends AtomicReference[Thread] {
  def lock(): Unit = {
    while (!compareAndSet(null, Thread.currentThread())) {
      Thread.onSpinWait()
    }
  }
  def unlock(): Unit = {
    lazySet(null)  // 比 volatile 写更轻量
  }
}
```

## ActorHouse 状态机

每个 Actor 包装在 `ActorHouse` 中，6 个状态，通过 CAS 转换：

```
CREATED(0) → MOUNTING(1) → WAITING(2) → READY(3) → SCHEDULED(4) → RUNNING(5)
```

`run()` 完成后：
- **非 barrier**：有更多消息 → READY（重新入队）；空 → WAITING
- **barrier 中**：有回复/事件 → READY；等待 barrier → WAITING（只接受回复/事件）

### 五个邮箱

| 邮箱 | 用途 |
|------|------|
| `replyMailbox` | 回复消息（最高优先级） |
| `exceptionMailbox` | 异常回复 |
| `askMailbox` | 入站 Ask 消息 |
| `noticeMailbox` | 入站 Notice 消息 |
| `eventMailbox` | 定时器/Reactor 事件 |

### 调度顺序

消息严格按照优先级顺序分发：
1. 回复（恢复挂起的 Stack）
2. 异常（也解除 barrier）
3. Ask（不在 barrier 时）
4. Notice（不在 barrier 时）
5. 事件（无论 barrier 状态都处理）
6. Channel future（仅 ChannelsActor）
7. 后续任务（仅 ChannelsActor）

### 高优先级条件

Actor 在以下任一条件下变为高优先级：
- `replyMailbox.size() > 2`——reply 积压，每条 reply 可完成一个 future 并可能解除挂起栈的阻塞
- `eventMailbox.size() > 4`——event 积压，系统事件需要及时处理
- `pendingPromiseCount == 0`——无下游阻塞，该 actor 没有等待回复的 ask，没有 stack 因等待下游而挂起，调度它不会遇到下游阻塞

## 工作窃取

当线程自身队列为空时，作为极端负载不均衡的安全兜底，尝试从其他线程窃取。主调度模型保持 actor 在挂载线程上执行以利用 CPU 缓存——窃取仅在线程真正空闲且另一线程确实过载时激活。

### 空闲检测

线程追踪连续事件循环迭代中三个队列均为空的次数（`idleCount`）。每当线程有工作时该计数器重置为 0。负载稳定但不太高的线程不会积累空闲迭代，因此不会窃取。

### 自适应窃取条件

窃取决策结合窃取者的 `idleCount` 与受害者的队列深度（`readies`）：

```
readies > STEAL_FLOOR  且  idleCount × readies >= STEAL_AGGRESSION
```

将窃取激进程度与不均衡严重程度关联：

| 受害者积压 | 窃取者所需连续空闲次数 | 原因 |
|-----------|---------------------|------|
| 128+ | 1 | 严重危机，立即响应 |
| 64+ | 2 | 中等积压，确认空闲 |
| 32+ | 4 | 轻度积压，保守策略 |
| < 32 | 永不窃取 | 自行排空快，缓存代价不值得 |

默认值：`STEAL_FLOOR = 32`、`STEAL_AGGRESSION = 128`（可通过 `cc.otavia.core.steal.floor` 和 `cc.otavia.core.steal.aggression` 配置）。

### 窃取机制

1. 随机选择起始位置，扫描所有其他线程
2. 使用 `tryLock` 尝试获取目标队列锁——锁争用时立即跳过下一个候选，不自旋等待
3. 每次只窃取 **1 个** ActorHouse 运行
4. 只窃取 StateActor（ChannelsActor 绑定了线程的 `IoHandler`）
5. 执行完毕后，被窃取的 actor 回到原线程的队列（actor 固定在挂载线程上）

## 线程池

`DefaultActorThreadPool` 默认创建 `availableProcessors` 个线程（可通过 `cc.otavia.actor.worker.size` 或 `cc.otavia.actor.worker.ratio` 配置）。

### Actor 放置策略

创建 actor 时，线程池通过 `TilingThreadSelector`（原子轮询）选择线程：

| `num` | 策略 |
|-------|------|
| 1 | 单个 actor 在一个线程上 → 返回 `ActorAddress` |
| `pool.size` | 每个线程一个 actor，全部标记 `isLoadBalance` → 返回带线程亲和的 `RobinAddress` |
| 其他 | 跨 `num` 个线程分配 → 返回轮询 `RobinAddress` |

IO actor（`ChannelsActor`）通过独立的 `ioSelector` 放置，将 IO actor 分配到不同线程上。

## 内存保护

可选的内存监控每 100ms 检查堆使用率。当使用率 > 90% 或空闲堆 < 100MB 时，设置 `busy = true`，导致 Notice 传递前休眠 40ms 作为背压。

## 关键设计：无上下文切换

`ActorThread` 直接在其线程上运行 actor。当 Stack 挂起（等待回复）时，它交出控制权。回复作为消息到达邮箱，当 house 被重新调度时，Stack 在**同一线程**上恢复。没有线程上下文切换 — 一切都在所属的 `ActorThread` 上进行。
