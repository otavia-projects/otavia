---
layout: main
title: Threading Model
---

# Threading Model

`otavia` uses an `ActorThread`-per-CPU model. Each `ActorThread` is an independent OS thread running its own event loop, with dedicated buffer pools and scheduling queues.

## ActorThread Main Loop

Each `ActorThread` runs a three-phase loop:

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

Executes NIO select and processes ready keys. The `ioCtx.canBlock` is true only when there are no runnable actors, no pending events, and no phantom refs to clean.

After each IO cycle, a `selectCnt` counter tracks consecutive empty wakeups. When it hits 512 (configurable), the Selector is rebuilt to work around the JDK epoll 100% CPU bug.

### Phase 2: IO Pipeline

Runs to completion with no time budget:

1. **`manager.runChannelsActors()`**: Fully drains the `channelsActorQueue` and `mountingQueue`
2. **`runThreadEvent()`**: Polls the `ConcurrentLinkedQueue[Event]` for timer/resource timeout events
3. **`stopActors()`**: Drains the `ReferenceQueue` for phantom-referenced actors that have been garbage collected

### Phase 3: Business

State actors run subject to a nanosecond `deadline`:

- `ioRatio == 100`: No deadline (unlimited)
- No IO events: `deadline = now + 500μs` (minimum budget)
- Default (`ioRatio == 50`): `actorTime = ioTime * (100 - ioRatio) / ioRatio`

The deadline is checked every 16th actor (`count & 0xF == 0`) to amortize `System.nanoTime()` syscall overhead.

### Thread Notification

When a message arrives for an actor on a different thread, `notifyThread()` calls `ioHandler.wakeup()` to interrupt any blocking `select()`.

## HouseManager

Each `ActorThread` has a `HouseManager` that manages three scheduling queues:

| Queue | Type | Purpose | Time Budget |
|-------|------|---------|-------------|
| `mountingQueue` | FIFOHouseQueue | Actors awaiting mount | Unlimited |
| `channelsActorQueue` | PriorityHouseQueue | IO pipeline actors | Unlimited |
| `actorQueue` | PriorityHouseQueue | State actors (business logic) | Subject to deadline |

### Routing

When an ActorHouse transitions to READY, `HouseManager.ready()` routes it:
- `STATE_ACTOR` → `actorQueue`
- `CHANNELS_ACTOR` / `SERVER_CHANNELS_ACTOR` → `channelsActorQueue`

## PriorityHouseQueue

A dual-queue design with separate linked lists for normal and high priority, each protected by its own `SpinLock`:

- **Dequeue**: Always checks high-priority queue first (strict priority)
- **SpinLock**: Based on `AtomicReference[Thread]`, CAS for lock, `lazySet(null)` for unlock

### SpinLock Implementation

```scala
class SpinLock extends AtomicReference[Thread] {
  def lock(): Unit = {
    while (!compareAndSet(null, Thread.currentThread())) {
      Thread.onSpinWait()
    }
  }
  def unlock(): Unit = {
    lazySet(null)  // cheaper than volatile write
  }
}
```

## ActorHouse State Machine

Each Actor is wrapped in an `ActorHouse` with six states, with transitions via CAS:

```
CREATED(0) → MOUNTING(1) → WAITING(2) → READY(3) → SCHEDULED(4) → RUNNING(5)
```

After `run()` completes:
- **Not in barrier**: Has more messages → READY (re-enqueue); empty → WAITING
- **In barrier**: Has reply/event → READY; waiting for barrier → WAITING (only accepts replies/events)

### Five Mailboxes

| Mailbox | Purpose |
|---------|---------|
| `replyMailbox` | Reply messages (highest priority) |
| `exceptionMailbox` | Exception replies |
| `askMailbox` | Incoming Ask messages |
| `noticeMailbox` | Incoming Notice messages |
| `eventMailbox` | Timer/Reactor events |

### Dispatch Order

Messages are dispatched in strict priority order:
1. Replies (resume suspended Stacks)
2. Exceptions (also release barriers)
3. Asks (if not in barrier)
4. Notices (if not in barrier)
5. Events (always, regardless of barrier)
6. Channel futures (ChannelsActor only)
7. Later tasks (ChannelsActor only)

### High Priority Conditions

An Actor becomes high priority when any of the following is true:
- `replyMailbox.size() > 2` — reply backlog; each reply completes a future and may unblock a suspended stack
- `eventMailbox.size() > 4` — event backlog; system events need timely processing
- `pendingPromiseCount == 0` — no downstream blocking; this actor has no outstanding asks waiting for replies, so no stack is suspended due to downstream dependencies. Scheduling this actor will not encounter such blocking

## Work Stealing

When a thread's own queues are empty, it attempts to steal from other threads as a safety net for extreme load imbalance. The primary scheduling model keeps actors on their owning thread for CPU cache locality — stealing only activates when a thread is genuinely idle and another thread is genuinely overwhelmed.

### Idle Detection

A thread tracks consecutive event-loop iterations where all three queues were empty (`idleCount`). This counter resets to 0 whenever the thread has work. A thread with moderate but steady load never accumulates idle iterations and never steals.

### Adaptive Steal Condition

The steal decision combines the thief's `idleCount` with the victim's queue depth (`readies`):

```
readies > STEAL_FLOOR  AND  idleCount × readies >= STEAL_AGGRESSION
```

This ties aggressiveness to imbalance severity:

| Victim backlog | Thief idle iterations needed | Rationale |
|----------------|------------------------------|-----------|
| 128+ | 1 | Severe crisis — immediate response |
| 64+ | 2 | Moderate backlog — confirm idleness |
| 32+ | 4 | Mild backlog — conservative |
| < 32 | never | Self-drains quickly; cache cost outweighs benefit |

Defaults: `STEAL_FLOOR = 32`, `STEAL_AGGRESSION = 128` (configurable via `cc.otavia.core.steal.floor` and `cc.otavia.core.steal.aggression`).

### Steal Mechanics

1. Picks a random starting index and scans all other threads
2. Uses `tryLock` on the victim's queue — if contended, skips to next candidate without spinning
3. Steals ONE house from the victim's `actorQueue` and runs it inline
4. Only state actors are stolen (`ChannelsActor`s are bound to their thread's `IoHandler`)
5. After execution, the stolen house re-enters the victim's queue (actors are pinned to their owning thread)

## Thread Pool

`DefaultActorThreadPool` creates `availableProcessors` threads by default (configurable via `cc.otavia.actor.worker.size` or `cc.otavia.actor.worker.ratio`).

### Actor Placement

When creating an actor, the thread pool selects threads via `TilingThreadSelector` (atomic round-robin):

| `num` | Strategy |
|-------|----------|
| 1 | Single actor on one thread → returns `ActorAddress` |
| `pool.size` | One actor per thread, all marked `isLoadBalance` → returns `RobinAddress` with thread affinity |
| Other | Distributes across `num` threads → returns `RobinAddress` with round-robin |

IO actors (`ChannelsActor`) are placed via a separate `ioSelector` to distribute IO actors across threads.

## Memory Protection

An optional memory monitor checks heap usage every 100 ms. When usage exceeds 90% or free heap is below 100 MB, `busy = true` is set, causing Notice delivery to sleep for 40 ms as back-pressure.

## Key Design: No Context Switches

An `ActorThread` runs actors directly on its thread. When a `Stack` suspends (waiting for a reply), it returns control. The reply arrives as a message in the mailbox, and when the house is re-scheduled, the Stack resumes on the **same thread**. There are no thread context switches — everything stays on the owning `ActorThread`.
