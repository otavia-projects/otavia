---
layout: main
title: SLF4A
---

# SLF4A - 异步日志

SLF4A 是 otavia 中的日志标准，参考了 SLF4J 的设计。它提供与 Actor 系统集成的异步日志框架。

## 获取 Logger

### 在 Actor 中

`StateActor` 和 `ChannelsActor` 已内置 `logger` 属性：

```scala
class MyActor extends StateActor[MyCall] {
  override protected def resumeNotice(stack: NoticeStack[MyNotice]): StackYield = {
    logger.info("收到 notice: {}", stack.notice)
    stack.return()
  }
}
```

### 在 Actor 外部

```scala
val logger = Logger.getLogger(getClass, system)
```

## 设计

SLF4A 参考 SLF4J 的设计，包含以下组件：

- **Logger**：日志接口，提供 `trace`、`debug`、`info`、`warn`、`error` 方法
- **LoggerFactory**：创建 Logger 实例
- **日志后端**：可插拔的后端实现

与 SLF4J 的关键区别是，Actor 上下文中的日志操作是异步的——日志消息通过 Actor 的邮箱系统发送，而非同步执行，防止日志阻塞 Actor 的消息处理。

## 日志级别

SLF4A 支持标准日志级别：
- `TRACE` — 细粒度调试信息
- `DEBUG` — 调试信息
- `INFO` — 信息性消息
- `WARN` — 警告条件
- `ERROR` — 错误条件

## 使用模式

```scala
class MyServiceActor extends StateActor[ServiceCall] {
  override protected def afterMount(): Unit = {
    logger.info("MyServiceActor 已挂载，id: {}", context.actorId)
  }

  override protected def resumeAsk(stack: AskStack[ServiceRequest]): StackYield = {
    logger.debug("处理请求: {}", stack.call)
    // ... 处理 ...
    logger.info("请求处理完成")
    stack.return(ServiceReply(result))
  }
}
```
