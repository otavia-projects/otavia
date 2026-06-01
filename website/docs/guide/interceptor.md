---
sidebar_position: 10
title: Actor Interceptor
---

# Actor Interceptor (AOP)

Actor applications have cross-cutting concerns — logging, authentication, rate limiting, metrics — that are orthogonal to business logic. Otavia implements AOP via **Address-level message interception**: a proxy actor wraps the target address, transparently intercepting messages without any change to the caller.

```
Caller ──ask──→ Interceptor Address ──ask──→ Target Actor Address
                 │                           │
                 │ pre-processing            │ business logic
                 │ (log/auth/rate-limit)     │
                 │                           │
                 │←── Reply ─────────────────│
                 │                           │
                 │ post-processing           │
                 │ (modify reply/record)     │
                 │                           │
                 ←── Reply ─────────────────
```

Key properties:
- **Transparent**: the caller holds an `Address[M]` — identical API whether the target is wrapped or not
- **Composable**: multiple interceptors can be chained (logging → auth → target)
- **Async-capable**: interceptors are full actors, can suspend/resume (e.g., async database lookup for auth)
- **Universal**: works for any `StateActor`, not limited to web handlers

## InterceptorActor

`InterceptorActor[M <: Call]` is the base class. It extends `StateActor[M]` and provides forwarding helpers:

```scala
class LoggingInterceptor(val next: Address[MyRequest])
    extends InterceptorActor[MyRequest] {

    override protected def resumeAsk(
        stack: AskStack[MyRequest & Ask[? <: Reply]]
    ): StackYield = {
        stack.state match {
            case _: StartState =>
                stack.attach(System.nanoTime())
                forwardAsk(stack) // forward to next, suspend
            case state: FutureState[_] if state.id == ForwardStateId =>
                val elapsed = (System.nanoTime() - stack.attach[Long]) / 1_000_000
                logger.info(s"Request took ${elapsed}ms")
                stack.`return`(state.future.getNow.asInstanceOf)
        }
    }
}
```

### Core API

| Member | Description |
|--------|-------------|
| `next: Address[M]` | The next interceptor or target actor. Provide via constructor parameter. |
| `ForwardStateId` | State ID (`-1`) used by `forwardAsk`. Use positive IDs for your own `FutureState` instances. |
| `forwardAsk(stack)` | Forward the ask to `next` and suspend. On reply, `resumeAsk` is called with `FutureState` whose `id == ForwardStateId`. |
| `forwardNotice(stack)` | Forward the notice to `next` and complete the stack. |

### Short-circuit

An interceptor can reply without forwarding — useful for auth rejection or rate limiting:

```scala
case _: StartState =>
    // Reject without calling forwardAsk
    stack.`return`(ErrorReply("unauthorized"))
```

## Programmatic API

Use `ActorSystem.intercept` to wrap a target address with interceptors:

```scala
val target  = system.buildActor(() => new MyHandler)
val proxied = system.intercept(target, Seq(
    next => new LoggingInterceptor(next),
    next => new AuthInterceptor(next)
))
// proxied: Address[MyRequest] — use this as the public address
```

Interceptors are applied in declaration order: first factory = outermost = first to process the request. The chain is: `Logging → Auth → Target → Auth resumes → Logging resumes`.

## @Intercept Annotation

Declare interceptors on an actor class via the `@Intercept` Java annotation (runtime retention is required for reflection):

```scala
@Intercept(Array(classOf[LoggingInterceptor], classOf[AuthInterceptor]))
class MyHandler extends StateActor[MyRequest] {
    deriveDispatch
    // handlers ...
}
```

When `buildActor` creates a `@Intercept`-annotated actor, the framework automatically builds the interceptor chain. The returned address is the outermost interceptor's address.

### perInstance

When the actor is created with `num > 1` (multiple instances via `RobinAddress`):

- `perInstance = true` (default): creates one interceptor per target instance, preserving parallelism
- `perInstance = false`: creates a single shared interceptor wrapping the `RobinAddress`

```scala
@Intercept(value = Array(classOf[LoggingInterceptor]), perInstance = false)
class SharedHandler extends StateActor[MyRequest]
```

## Interceptor with Async Pre-check

Interceptors can perform async operations (e.g., database lookup) before forwarding:

```scala
class AuthInterceptor(val next: Address[MyRequest], authDb: Address[AuthCheck])
    extends InterceptorActor[MyRequest] {

    override protected def resumeAsk(
        stack: AskStack[MyRequest & Ask[? <: Reply]]
    ): StackYield = {
        stack.state match {
            case _: StartState =>
                // Async auth check (state ID = 1)
                val state = FutureState[AuthResult](1)
                authDb.ask(AuthCheck(stack.ask.token), state.future)
                stack.suspend(state)
            case state: FutureState[_] if state.id == 1 =>
                val result = state.future.getNow.asInstanceOf[AuthResult]
                if (result.authorized) forwardAsk(stack) // proceed
                else stack.`return`(ErrorReply("forbidden"))
            case state: FutureState[_] if state.id == ForwardStateId =>
                stack.`return`(state.future.getNow.asInstanceOf)
        }
    }
}
```
