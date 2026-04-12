<div align=center>
<img src="docs/_assets/images/logo.drawio.svg" alt="otavia" width="200">
</div>
<h1 align=center>Otavia</h1>

<p align=center><b>基于 Scala 3 的高性能 IO & Actor 框架</b></p>

<p align=center>
像写单线程程序一样写并发程序 —— 无锁、无回调、无运行时开销。
</p>

<p align=center>

![GitHub](https://img.shields.io/github/license/otavia-projects/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)
![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3-blue)
[![Unit Tests](https://github.com/otavia-projects/otavia/actions/workflows/unittest.yml/badge.svg)](https://github.com/otavia-projects/otavia/actions/workflows/unittest.yml)
![Maven Central](https://img.shields.io/maven-central/v/cc.otavia/otavia-runtime_3)
</p>

<p align=center>
<a href="./README.MD">English</a> · <a href="./docs/_docs/zh/quick_start.md">快速入门</a> · <a href="https://otavia.cc/home.html">文档</a> · <a href="./docs/_docs/zh/core_concept.md">核心概念</a>
</p>

> **注意**：本项目仍在积极开发中，许多组件尚未完成，**暂不具备生产可用性**。

---

## 为什么选择 Otavia？

写并发程序很难。线程、锁、竞态条件、回调地狱 —— Otavia 通过将两个经过验证的模型合二为一，从根本上消除了这些问题：

**Actor 模型** —— 每个 Actor 内部完全单线程执行，只通过类型安全的消息通信。没有锁、没有 synchronized、没有并发 bug。

**Netty IO 栈** —— 从 [Netty](https://netty.io) 移植而来的、久经生产验证的 Channel Pipeline 架构，支持 TCP、UDP 和文件 IO。

结果：你在 Actor 内部编写的就是清晰的单线程代码，Otavia 负责调度、IO 多路复用、零分配消息传递和背压控制。

### 有什么不同？

| | Otavia |
|---|---|
| **编译时类型安全** | 发错了消息类型？编译不通过。`ReplyOf[A]` 自动推导回复类型。 |
| **零分配热路径** | 信封、栈、Promise、状态 —— 全部对象池化，消息处理过程中零 GC 压力。 |
| **IO 与 Actor 同线程** | IO 事件和业务逻辑在同一线程处理，零上下文切换。 |
| **零外部依赖** | 核心运行时仅依赖 JDK，不引入任何第三方包。 |
| **百万级 Actor** | 秒级创建百万 Actor 实例，发送数亿条消息。 |

## 体验 Otavia

定义类型安全的消息：

```scala
case class Pong(pingId: Int) extends Reply
case class Ping(id: Int) extends Ask[Pong]   // 回复类型在编译时绑定
case class Start(sid: Int) extends Notice
```

实现 Actor —— 单线程，无锁：

```scala
final class PongActor extends StateActor[Ping] {
  override def resumeAsk(stack: AskStack[Ping]): StackYield =
    stack.`return`(Pong(stack.ask.id))   // 类型安全的回复
}

final class PingActor(pongActor: Address[Ping]) extends StateActor[Start] {
  override def resumeNotice(stack: NoticeStack[Start]): StackYield = stack.state match {
    case _: StartState =>
      val state = FutureState[Pong]()
      pongActor.ask(Ping(stack.notice.sid), state.future)  // 异步、非阻塞
      stack.suspend(state)                                  // 等待回复
    case state: FutureState[Pong] =>
      println(s"Received ${state.future.getNow}")           // 在这里恢复
      stack.`return`()
  }
}
```

启动：

```scala
@main def run(): Unit =
  val system = ActorSystem()
  val pong = system.buildActor(() => new PongActor())
  val ping = system.buildActor(() => new PingActor(pong))
  ping.notice(Start(88))
```

无需配置线程池，无需加锁，无需回调，无需 `Future` 链。只有类型安全的消息传递和基于栈的续延机制。

## 特性

- **全链路异步** —— 无阻塞、无线程挂起，一切都是事件驱动的。
- **忘掉线程、忘掉锁** —— Actor 内部一切代码单线程运行。
- **弹性设计** —— 基于《反应性宣言》原则，支持 Actor 自愈和重启策略。
- **零成本 Ask-Pattern** —— 发送 Ask 并接收 Reply 就像调用方法一样，开销极低。
- **强大的 IO 栈** —— Netty 的 `ChannelPipeline` + `ChannelHandler` 架构，并支持文件通道。
- **CPS async/await** —— 基于 Scala 3 元编程的栈续延机制，告别回调地狱。
- **Actor 依赖注入** —— 编译时类型安全的 IoC 容器。
- **丰富的协议编解码** —— HTTP、Redis、DNS、MQTT、SMTP、SOCKS、HAProxy、Memcache、MySQL、PostgreSQL。

## 生态

| 模块 | 说明 |
|------|------|
| **core** (`otavia-runtime`) | Actor 系统、Channel、消息处理、定时器 |
| **buffer** (`otavia-buffer`) | 高性能缓冲区管理，提供 `AdaptiveBuffer` |
| **codec-http** (`otavia-codec-http`) | HTTP 客户端和服务端编解码 |
| **codec-redis** (`otavia-codec-redis`) | Redis 协议编解码 |
| **codec-dns** / **codec-mqtt** / **codec-smtp** | DNS / MQTT / SMTP 协议编解码 |
| **codec-socks** / **codec-haproxy** / **codec-memcache** | SOCKS / HAProxy / Memcached 协议编解码 |
| **serde-json** (`otavia-serde-json`) | JSON 序列化，支持宏自动派生 |
| **serde-proto** (`otavia-serde-proto`) | Protocol Buffers 序列化（开发中） |
| **sql** + **sql-mysql-driver** / **sql-postgres-driver** | SQL 抽象层，含 MySQL 和 PostgreSQL 驱动 |
| **log4a** (`otavia-log4a`) | 异步日志框架 |
| **testkit** (`otavia-testkit`) | Actor 测试工具 |

外部生态：从 Netty 和 [Eclipse Vert.x](https://vertx.io/) 移植的协议编解码 —— [otavia-projects](https://github.com/otavia-projects)。

## 快速开始

添加依赖：

```scala
// sbt
libraryDependencies += "cc.otavia" %% "otavia-all" % "@MAVEN@"

// Mill
ivy"cc.otavia::otavia-all:@MAVEN@"

// Maven
// <groupId>cc.otavia</groupId> <artifactId>otavia-all_3</artifactId>
```

然后阅读[快速入门](./docs/_docs/zh/quick_start.md)指南。

## 从源码构建

需要 JDK 17+，使用 [Mill](https://mill-build.com/) 构建。

```bash
./mill __.compile    # 编译所有模块
./mill __.test       # 运行所有测试
./mill core.test     # 运行单个模块的测试
```

架构详情和开发指南请参见 [CLAUDE.md](./CLAUDE.md)。

## 贡献

欢迎任何形式的贡献！

## 许可证

[Apache License 2.0](./LICENSE)
