---
title: Actor 拦截器
---

# Actor 拦截器（AOP）

Actor 应用中存在横切关注点——日志、认证、限流、指标采集——与业务逻辑正交。Otavia 通过 **Address 层消息拦截** 实现 AOP：代理 Actor 包装目标地址，透明地拦截消息，调用方无需任何改动。

```
调用方 ──ask──→ 拦截器 Address ──ask──→ 目标 Actor Address
                 │                           │
                 │ 前置处理                   │ 业务逻辑
                 │ (日志/认证/限流)            │
                 │                           │
                 │←── Reply ─────────────────│
                 │                           │
                 │ 后置处理                   │
                 │ (修改响应/记录指标)          │
                 │                           │
                 ←── Reply ─────────────────
```

核心特性：
- **透明性**：调用方持有 `Address[M]`——无论目标是否被包装，API 完全一致
- **可组合**：多个拦截器可以链式叠加（日志 → 认证 → 目标）
- **异步能力**：拦截器是完整的 Actor，可以 suspend/resume（如异步查库做认证）
- **通用性**：适用于任何 `StateActor`，不局限于 Web 处理器

## InterceptorActor

`InterceptorActor[M <: Call]` 是基类，继承 `StateActor[M]`，提供转发辅助方法：

```scala
class LoggingInterceptor(val next: Address[MyRequest])
    extends InterceptorActor[MyRequest] {

    override protected def resumeAsk(
        stack: AskStack[MyRequest & Ask[? <: Reply]]
    ): StackYield = {
        stack.state match {
            case _: StartState =>
                stack.attach(System.nanoTime())
                forwardAsk(stack) // 转发到下一层，挂起
            case state: FutureState[_] if state.id == ForwardStateId =>
                val elapsed = (System.nanoTime() - stack.attach[Long]) / 1_000_000
                logger.info(s"请求耗时 ${elapsed}ms")
                stack.`return`(state.future.getNow.asInstanceOf)
        }
    }
}
```

### 核心 API

| 成员 | 说明 |
|------|------|
| `next: Address[M]` | 下一个拦截器或目标 Actor。通过构造参数提供。 |
| `ForwardStateId` | `forwardAsk` 使用的状态 ID（`-1`）。自定义 `FutureState` 请使用正数 ID。 |
| `forwardAsk(stack)` | 将 ask 转发给 `next` 并挂起。收到回复后，`resumeAsk` 以 `FutureState`（`id == ForwardStateId`）被调用。 |
| `forwardNotice(stack)` | 将 notice 转发给 `next` 并完成栈。 |

### 短路拦截

拦截器可以不转发直接返回——适用于认证拒绝或限流：

```scala
case _: StartState =>
    // 不调用 forwardAsk，直接返回
    stack.`return`(ErrorReply("unauthorized"))
```

## 编程式 API

使用 `ActorSystem.intercept` 包装目标地址：

```scala
val target  = system.buildActor(() => new MyHandler)
val proxied = system.intercept(target, Seq(
    next => new LoggingInterceptor(next),
    next => new AuthInterceptor(next)
))
// proxied: Address[MyRequest] — 作为公开地址使用
```

拦截器按声明顺序应用：第一个工厂 = 最外层 = 最先处理请求。链路为：`日志 → 认证 → 目标 → 认证恢复 → 日志恢复`。

## @Intercept 注解

通过 `@Intercept` Java 注解（运行时保留，反射读取所需）在 Actor 类上声明拦截器：

```scala
@Intercept(Array(classOf[LoggingInterceptor], classOf[AuthInterceptor]))
class MyHandler extends StateActor[MyRequest] {
    deriveDispatch
    // handlers ...
}
```

当 `buildActor` 创建带有 `@Intercept` 注解的 Actor 时，框架自动构建拦截器链。返回的地址是最外层拦截器的地址。

### perInstance

当 Actor 以 `num > 1`（通过 `RobinAddress` 创建多实例）创建时：

- `perInstance = true`（默认）：为每个目标实例创建一个拦截器，保持并行度
- `perInstance = false`：创建一个共享拦截器包装 `RobinAddress`

```scala
@Intercept(value = Array(classOf[LoggingInterceptor]), perInstance = false)
class SharedHandler extends StateActor[MyRequest]
```

## 带异步前置检查的拦截器

拦截器可以在转发前执行异步操作（如数据库查询）：

```scala
class AuthInterceptor(val next: Address[MyRequest], authDb: Address[AuthCheck])
    extends InterceptorActor[MyRequest] {

    override protected def resumeAsk(
        stack: AskStack[MyRequest & Ask[? <: Reply]]
    ): StackYield = {
        stack.state match {
            case _: StartState =>
                // 异步认证检查（状态 ID = 1）
                val state = FutureState[AuthResult](1)
                authDb.ask(AuthCheck(stack.ask.token), state.future)
                stack.suspend(state)
            case state: FutureState[_] if state.id == 1 =>
                val result = state.future.getNow.asInstanceOf[AuthResult]
                if (result.authorized) forwardAsk(stack) // 继续转发
                else stack.`return`(ErrorReply("forbidden"))
            case state: FutureState[_] if state.id == ForwardStateId =>
                stack.`return`(state.future.getNow.asInstanceOf)
        }
    }
}
```
