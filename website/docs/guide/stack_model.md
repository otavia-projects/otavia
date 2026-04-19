---
sidebar_position: 3
title: Stack Execution Model
---

# Stack Execution Model

The Stack is otavia's most distinctive innovation. It replaces traditional callbacks and coroutines with a manually managed state machine using pooled objects.

## Core Concepts

### Stack

`Stack` is the execution frame that manages the lifecycle of processing a single message. It maintains two doubly-linked promise chains:

- **Uncompleted chain** (`uncompletedHead/uncompletedTail`): Promises for Ask messages that have not yet received replies
- **Completed chain** (`completedHead/completedTail`): Promises whose replies have arrived

Each Stack contains:
- `stackState: StackState` — Current state in the state machine
- `nextState: StackState` — State to transition to on suspend
- `actor: AbstractActor` — The owning actor

### StackState

An interface with `resumable(): Boolean` and `id: Int`. User-defined states implement this (often with `Poolable` for recycling). `StackState.start` is a singleton initial state whose `resumable()` method returns `true`.

### StackYield

A sealed trait with two singletons:
- **`SUSPEND`** (`completed = false`): Stack is suspended, waiting for async operations
- **`RETURN`** (`completed = true`): Stack is done, can be recycled

### suspend and return

```scala
// Suspend: save the next state and return SUSPEND
def suspend(state: StackState): StackYield = {
  this.nextState = state
  StackYield.SUSPEND
}

// Return: the Stack calls this to complete
// For NoticeStack:
stack.return()        // returns StackYield.RETURN

// For AskStack:
stack.return(reply)   // sends reply back to sender, returns StackYield.RETURN
```

## Stack Types

| Type | Purpose | Return Method |
|------|---------|---------------|
| `NoticeStack[N]` | Manages Notice execution | `return()` |
| `AskStack[A]` | Manages Ask execution | `return(reply)` or `throw(cause)` |
| `ChannelStack[T]` | Manages Channel inbound messages | `return(result)` |
| `BatchNoticeStack[N]` | Batch Notice execution | `return()` |
| `BatchAskStack[A]` | Batch Ask execution | `return(reply)` or `return(Seq[(Envelope, Reply)])` |

### AskStack

`AskStack` tracks the Ask message, the sender's Address, and the askId for reply correlation:

```scala
// User implements:
override protected def resumeAsk(stack: AskStack[MyAsk]): StackYield = {
  stack.state.id match {
    case StartState.id =>
      val state = FutureState[MyReply]()
      target.ask(stack.ask.asInstanceOf[MyAsk], state.future)
      stack.suspend(state)
    case FutureState.id =>
      val reply = stack.state.asInstanceOf[FutureState[MyReply]].future.getNow
      // process reply...
      stack.return(MyReply(...))
  }
}
```

### ChannelStack

Unlike other Stack types, `ChannelStack` extends both `Stack` and `QueueMapEntity`, integrating with the Channel's inflight `QueueMap`. ChannelStacks are NOT recycled by `switchState` — they are managed by the Channel's inflight queue.

## Future and Promise

### Design Philosophy

`otavia` implements its own `Future/Promise` system, separate from Scala's standard library. This system is designed for zero-allocation operation:

- `MessagePromise[R]` **IS** `MessageFuture[R]` — they are the same object, with no wrapper allocation
- `MessagePromise` **IS** the hash node in `FutureDispatcher` — it has `hashNext` directly, with no wrapper
- All promises are pooled via `ActorThreadIsolatedObjectPool`

### MessagePromise / MessageFuture

Used for Actor-to-Actor Ask/Reply:
- `aid: Long` — Message ID (hash key in FutureDispatcher)
- `tid: Long` — Timeout registration ID (for cancellation)
- `result: AnyRef` / `error: Throwable` — Completion state
- `stack: Stack` — The owning Stack

### ChannelPromise / ChannelFuture

Used for Actor-to-Channel request/response:
- `ch: Channel` — The associated channel
- `ask: AnyRef` — The channel operation request
- `callback: ChannelPromise => Unit` — Optional completion callback

## Helper States

### FutureState

`FutureState[R]` combines a `StackState` with a `MessageFuture[R]`. This is the standard user-facing mechanism:

```scala
val state = FutureState[MyReply]()
targetAddress.ask(myAsk, state.future)
stack.suspend(state)
// When resumed:
val reply = state.future.getNow
```

### ChannelFutureState

Same pattern but wraps a `ChannelFuture` for async channel operations (bind, connect, register).

### StartState

Singleton initial state. `resumable() = true` so the first promise completion always triggers a resume.

## FutureDispatcher

`FutureDispatcher` is a custom open-addressed hash table (with chaining) that maps `Long` message IDs to `MessagePromise` instances. It is mixed into `AbstractActor`.

Design choices:
- `loadFactor = 2.0` (grows when `contentSize + 1 >= threshold`)
- `index(id) = (id & mask).toInt` -- bitmask indexing
- Collision resolution via `MessagePromise.hashNext` linked list (no wrapper objects)
- Auto-shrinks when the table has grown to 4x its initial capacity but is less than 50% full

## State Machine Transitions

### switchState (the core transition engine)

**SUSPEND path**:
1. Get `oldState` from Stack, get `nextState` (set by `suspend`)
2. If state changed: set new state, recycle old state (if `Poolable`)
3. Assert the Stack still has uncompleted promises

**RETURN path**:
1. Recycle current state
2. Assert `stack.isDone`
3. If not a `ChannelStack`, recycle the entire Stack to the pool

### handlePromiseCompleted (the resume logic)

When a Reply arrives and completes a Promise:
1. `stack.moveCompletedPromise(promise)` — transfer from uncompleted to completed chain
2. Check: `stack.state.resumable()` OR `!stack.hasUncompletedPromise`
3. If resumable: re-dispatch the Stack (e.g., `dispatchAskStack(stack)`)

## Complete Ask/Reply Lifecycle

### Phase 1: Sending

```
ActorA.ask(myAsk) → create FutureState[Reply]
  → PhysicalAddress.packaging → Envelope from pool
  → sender.attachStack(messageId, future):
      promise.stack = currentStack
      promise.id = messageId
      currentStack.addUncompletedPromise(promise)
      FutureDispatcher.push(promise)
  → houseB.putAsk(envelope)
```

### Phase 2: Processing

```
ActorHouseB.run() → dispatchAsks()
  → receiveAsk → AskStack from pool → setAsk → dispatchAskStack
  → resumeAsk(stack) runs user code
  → stack.return(reply):
      sender.reply(reply, askId) → reply Envelope → houseA.putReply()
```

### Phase 3: Resuming

```
ActorHouseA.run() → dispatchReplies() (highest priority!)
  → receiveReply → extract reply + replyId
  → FutureDispatcher.pop(replyId) → MessagePromise
  → promise.setSuccess(reply)
  → handlePromiseCompleted(stack, promise):
      moveCompletedPromise (uncompleted → completed)
      state.resumable() == true
      → dispatchAskStack(stack) (re-dispatch!)
  → resumeAsk runs again with new state
  → state.future.getNow → process reply → stack.return(finalReply)
```

![](/img/stack_resume.drawio.svg)

## Object Pooling

The pooling strategy is aggressive and multi-layered to achieve zero-GC operation:

1. **Thread-isolated pools**: Every pooled type has its own `ActorThreadIsolatedObjectPool` with a per-thread `SingleThreadPoolableHolder`
2. **Creator affinity**: Objects recycled on a different thread than their creator thread are simply dropped (reclaimed by GC), avoiding cross-thread pool synchronization
3. **Idle cleanup**: 60-second timeout trigger reduces pools unused for 30 seconds to max 10 objects
4. **Stack-level lifecycle**: Completed promises recycled when `setState` is called. Entire stacks recycled via `recycleStack`
5. **Envelope immediate recycling**: Envelopes recycled as soon as data is extracted, not held for the Stack's lifetime
6. **No wrapper objects**: `MessagePromise` = `MessageFuture` = hash node (three-in-one)
