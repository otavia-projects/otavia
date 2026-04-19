---
sidebar_position: 11
title: SLF4A
---

# SLF4A - Asynchronous Logging

`SLF4A` is the logging standard in `otavia`, referencing the design of SLF4J. It provides an asynchronous logging framework that integrates with the Actor system.

## Getting a Logger

### In Actors

`StateActor` and `ChannelsActor` already have a built-in `logger` property:

```scala
class MyActor extends StateActor[MyCall] {
  override protected def resumeNotice(stack: NoticeStack[MyNotice]): StackYield = {
    logger.info("Received notice: {}", stack.notice)
    stack.return()
  }
}
```

### Outside Actors

```scala
val logger = Logger.getLogger(getClass, system)
```

## Design

`SLF4A` references SLF4J's design with the following components:

- **`Logger`**: The logging interface providing `trace`, `debug`, `info`, `warn`, `error` methods
- **`LoggerFactory`**: Creates `Logger` instances
- **Logging backend**: Pluggable backend implementations

The key difference from SLF4J is that logging operations in the Actor context are asynchronous — log messages are sent through the Actor's mailbox system rather than executing synchronously, preventing logging from blocking the Actor's message processing.

## Log Levels

SLF4A supports the standard log levels:
- `TRACE` — Fine-grained debug information
- `DEBUG` — Debug information
- `INFO` — Informational messages
- `WARN` — Warning conditions
- `ERROR` — Error conditions

## Usage Pattern

```scala
class MyServiceActor extends StateActor[ServiceCall] {
  override protected def afterMount(): Unit = {
    logger.info("MyServiceActor mounted with id {}", context.actorId)
  }

  override protected def resumeAsk(stack: AskStack[ServiceRequest]): StackYield = {
    logger.debug("Processing request: {}", stack.call)
    // ... process ...
    logger.info("Request completed successfully")
    stack.return(ServiceReply(result))
  }
}
```
