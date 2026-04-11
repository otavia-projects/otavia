---
layout: main
title: Address Model
---

# Address Model

Actors are isolated from each other and can only communicate through `Address` instances. The Address model provides compile-time type safety and flexible routing strategies.

## Address Trait

`Address[-M <: Call]` is the root trait (contravariant in `M`). It provides the core messaging API:

```scala
trait Address[-M <: Call] {
  def notice(notice: M & Notice): Unit
  def ask(ask: M & Ask[? <: Reply]): FutureState[? <: Reply]
  def ask(ask: M & Ask[? <: Reply], future: Future[? <: Reply]): Unit
  def reply(reply: Reply, replyId: Long, sender: AbstractActor[?]): Unit
}
```

The contravariance means `Address[Call]` can send any message type, while `Address[MyNotice]` can only send `MyNotice` messages.

## PhysicalAddress

`PhysicalAddress[M]` is the concrete address bound to a single `ActorHouse`. It is the base class for all address types that directly route messages to a specific actor's mailboxes.

### Message Routing

When sending a message via `PhysicalAddress`:

**Ask** (`ask` method):
1. `packaging(ask, sender)` — Creates an `Envelope` from the pool, sets the sender, messageId, and content
2. `sender.attachStack(envelope.messageId, future)` — Wires the promise to the sender's current `Stack` and `FutureDispatcher`
3. `house.putAsk(envelope)` — Places into the target actor's `askMailbox`

**Notice** (`notice` method):
1. Creates an `Envelope` (no sender needed)
2. `house.putNotice(envelope)` — Places into the target actor's `noticeMailbox`

**Reply** (`reply` method):
1. Creates an `Envelope` with a `replyId` (single) or `replyIds` (array)
2. `house.putReply(envelope)` — Places into the sender actor's `replyMailbox`

### Timeout Support

When sending an Ask with a timeout:
```scala
address.ask(myAsk, future, timeout)
```
After the ask is sent, a timer task is registered via `sender.timer.registerAskTimeout(...)`. If the timeout fires before a reply arrives, a `TimeoutReply` is delivered instead.

## ActorAddress

`ActorAddress[M]` is the simplest address type -- a final class extending `PhysicalAddress`. Each `ActorHouse` creates exactly one `ActorAddress` for its actor.

```scala
final class ActorAddress[M <: Call](house: ActorHouse) extends PhysicalAddress[M]
```

## RobinAddress

`RobinAddress[M]` provides round-robin load balancing over an array of `ActorAddress` instances. It is created when `ActorSystem.buildActor` is called with `num > 1`.

### Routing Strategies

**Notice routing**:
Simple round-robin: `noticeCursor % underlying.length`

**Ask routing** — two modes:

1. **Thread affinity (Load Balance mode)**: When `sender.isLoadBalance && isLB` is true, it routes to `underlying(sender.mountedThreadId)` -- sends to the worker on the **same thread**, avoiding cross-thread mailbox writes. This is the optimal path.

2. **Round-robin**: Otherwise, uses `askCursor % underlying.length`

### When RobinAddress is Created

- `num == pool.size`: One actor per thread, all marked `isLoadBalance = true`. The `RobinAddress` with `isLB = true` enables thread-affinity routing.
- `num > 1` (other): Distributes actors across selected threads with simple round-robin.

## EventableAddress

`EventableAddress` is a trait with an `inform(event)` method for timer/event delivery. `PhysicalAddress` overrides this to put events into the house's `eventMailbox`.

There is also `ActorThreadAddress`, which routes events to the thread's `eventQueue` (`ConcurrentLinkedQueue`) — used for `ResourceTimeoutEvent`s that need thread-level processing.

## Address and Type Safety

The type system ensures that you cannot send the wrong message type to an Actor:

```scala
case class Ping extends Notice
case class Pong extends Notice
case class AskData extends Ask[DataReply]
case class DataReply(value: String) extends Reply

class PingActor extends StateActor[Ping | AskData]

// Creating the actor returns a typed Address:
val address: Address[Ping | AskData] = system.buildActor[PingActor](ActorFactory[PingActor])

// Compile-time safe:
address.notice(Ping())        // OK
address.ask(AskData())        // OK

// Compile-time error:
// address.notice(Pong())     // Error: Pong is not Ping | AskData
```
