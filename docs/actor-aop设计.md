# Actor AOP 设计方案

## 1. 为什么需要这个特性

### 问题

Actor 应用中存在大量横切关注点，与业务逻辑正交，需要在消息处理前后统一执行。典型场景：

- **日志**：记录每个请求的方法、路径、耗时
- **认证/鉴权**：验证 token，提取用户身份
- **限流**：控制请求频率
- **CORS**：为响应添加跨域头
- **指标采集**：QPS、延迟分布、错误率
- **链路追踪**：传递 trace id

这些关注点的共同特征：
1. **与业务无关**：不应该是 handler 逻辑的一部分
2. **需要统一执行**：遗漏某个路由就产生 bug
3. **需要组合**：一个请求可能经过日志 + 认证 + 限流三层

### 为什么不在 Web 模块做

传统 Web 框架用 Filter/Interceptor 链解决，但这带来问题：

1. **Filter 是同步的**：如果认证需要查数据库，要么阻塞 ActorThread，要么绕过 Filter 机制
2. **Filter 是 Web 专属概念**：非 Web 的 Actor（定时任务、消息队列消费者、RPC 服务）同样需要横切关注点
3. **Filter 引入新的编程模型**：用户需要同时理解 Actor 模型和 Filter 模型，增加心智负担

### Actor 天然适合 AOP

Actor 之间通过 Address 通信。消息发送方只持有目标 Actor 的 Address，不关心 Address 背后是原始 Actor 还是代理。这意味着：

1. **在 Address 层面拦截消息**：插入一个 Proxy Actor，转发消息给目标 Actor，拦截器逻辑在 Proxy 中执行
2. **发送方完全无感知**：不需要修改任何调用方代码
3. **天然支持异步**：Proxy 本身是 Actor，可以使用完整的栈协程能力
4. **适用于所有 Actor**：不只是 WebActor，任何 Actor 都可以通过 Address 代理实现 AOP

这是比传统 Filter/Interceptor 更通用的解决方案，因为它作用在 Actor 通信的基础层——Address。

## 2. 设计方向

核心思路：**通过 Address 代理实现消息拦截**。

```
发送方 ──ask──→ Proxy Address ──ask──→ 目标 Actor Address
                │                      │
                │ 拦截前逻辑            │ 业务逻辑
                │ (日志/认证/限流)       │
                │                      │
                │←── Reply ────────────│
                │                      │
                │ 拦截后逻辑            │
                │ (修改响应/记录指标)    │
                │                      │
                ←── Reply ────────────
```

关键特性：
- **透明性**：发送方持有 Proxy Address，与持有原始 Address 的用法完全一致
- **可组合**：多层 Proxy 可以嵌套（日志 Proxy → 认证 Proxy → 目标 Actor）
- **异步能力**：Proxy 是 Actor，可以做 ask/suspend（如异步查数据库验证 token）
- **Actor 模型一致**：不引入新的编程模型，就是 Actor 之间的消息传递

## 3. 后续

具体实现方案（Proxy Actor 模式、Address 包装机制、拦截器定义 API、性能优化等）将在单独的设计会话中展开。本文档仅说明需求背景和设计方向。
