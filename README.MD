<div align=center>
<img src="website/static/img/logo.drawio.svg" alt="otavia" width="200">
</div>
<h1 align=center>Otavia</h1>

<p align=center><b>High-performance IO & Actor framework for Scala 3</b></p>

<p align=center>
Write concurrent programs as if they were single-threaded — no locks, no callbacks, no runtime overhead.
</p>

<p align=center>
<img src="https://img.shields.io/github/license/otavia-projects/otavia" alt="GitHub">
<a href="https://otavia-projects.github.io/otavia/home.html"><img src="https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg" alt="GitHub Pages"></a>
<img src="https://img.shields.io/badge/JDK-17%2B-blue" alt="JDK 17+">
<img src="https://img.shields.io/badge/Scala-3.3-blue" alt="Scala 3.3">
<a href="https://github.com/otavia-projects/otavia/actions/workflows/unittest.yml"><img src="https://github.com/otavia-projects/otavia/actions/workflows/unittest.yml/badge.svg" alt="Unit Tests"></a>
<img src="https://img.shields.io/maven-central/v/cc.otavia/otavia-runtime_3" alt="Maven Central">
</p>

<p align=center>
<a href="./README.zh_cn.md">简体中文</a> · <a href="./docs/_docs/quick_start.md">Quick Start</a> · <a href="https://otavia.cc/home.html">Documentation</a> · <a href="./docs/_docs/core_concept.md">Core Concepts</a>
</p>

> **Note**: This project is under active development. Many components are still incomplete and it is **not yet production-ready**.

---

## Why Otavia?

Writing concurrent programs is hard. Threads, locks, race conditions, callback hell — Otavia eliminates all of them by combining two proven models into one framework:

**The Actor model** — each actor is single-threaded, communicating only via typed messages. No locks, no synchronized blocks, no concurrency bugs.

**The Netty IO stack** — ported from [Netty](https://netty.io), production-proven channel pipeline architecture for TCP, UDP, and file IO.

The result: you write straightforward single-threaded code inside actors, and Otavia handles the rest — scheduling, IO multiplexing, zero-allocation message passing, and back-pressure.

### What makes it different?

| | Otavia |
|---|---|
| **Compile-time type safety** | Send the wrong message type? It won't compile. `ReplyOf[A]` extracts reply types automatically. |
| **Zero-allocation hot paths** | Envelopes, stacks, promises, states — all object-pooled. No GC pressure during message processing. |
| **IO + Actor on the same thread** | No context switches between IO events and business logic. One event loop handles both. |
| **Zero external dependencies** | The core runtime depends on nothing but the JDK. |
| **Millions of actors** | Create millions of actor instances and send billions of messages in seconds. |

## A Taste of Otavia

Define type-safe messages:

```scala
case class Pong(pingId: Int) extends Reply
case class Ping(id: Int) extends Ask[Pong]   // reply type is wired at compile time
case class Start(sid: Int) extends Notice
```

Implement actors — single-threaded, no locks:

```scala
final class PongActor extends StateActor[Ping] {
  override def resumeAsk(stack: AskStack[Ping]): StackYield =
    stack.`return`(Pong(stack.ask.id))   // type-safe reply
}

final class PingActor(pongActor: Address[Ping]) extends StateActor[Start] {
  override def resumeNotice(stack: NoticeStack[Start]): StackYield = stack.state match {
    case _: StartState =>
      val state = FutureState[Pong]()
      pongActor.ask(Ping(stack.notice.sid), state.future)  // async, non-blocking
      stack.suspend(state)                                  // wait for reply
    case state: FutureState[Pong] =>
      println(s"Received ${state.future.getNow}")           // resume here
      stack.`return`()
  }
}
```

Start:

```scala
@main def run(): Unit =
  val system = ActorSystem()
  val pong = system.buildActor(() => new PongActor())
  val ping = system.buildActor(() => new PingActor(pong))
  ping.notice(Start(88))
```

No thread pools to configure. No locks. No callbacks. No `Future` chains. Just type-safe message passing with stack-based continuations.

## Features

- **Fully Asynchronous** — no blocking, no thread suspension, everything is event-driven.
- **Forget Threads, Forget Locks** — everything runs single-threaded within an Actor.
- **Resilient by Design** — built on [The Reactive Manifesto](https://www.reactivemanifesto.org/) principles: self-healing actors with restart strategies.
- **Zero-Cost Ask-Pattern** — send an Ask and receive a Reply like calling a method, with near-zero overhead.
- **Powerful IO Stack** — Netty's `ChannelPipeline` + `ChannelHandler` architecture, with file channel support.
- **CPS-based async/await** — stack-based continuations via Scala 3 metaprogramming, no callback hell.
- **Actor Dependency Injection** — compile-time type-safe IoC container for actor addresses.
- **Rich Protocol Codecs** — HTTP, Redis, DNS, MQTT, SMTP, SOCKS, HAProxy, Memcache, MySQL, PostgreSQL.

## Ecosystem

| Module | Description |
|--------|-------------|
| **core** (`otavia-runtime`) | Actor system, channels, message handling, timers |
| **buffer** (`otavia-buffer`) | High-performance buffer management with `AdaptiveBuffer` |
| **codec-http** (`otavia-codec-http`) | HTTP client and server codec |
| **codec-redis** (`otavia-codec-redis`) | Redis protocol codec |
| **codec-dns** / **codec-mqtt** / **codec-smtp** | DNS / MQTT / SMTP protocol codecs |
| **codec-socks** / **codec-haproxy** / **codec-memcache** | SOCKS / HAProxy / Memcached protocol codecs |
| **serde-json** (`otavia-serde-json`) | JSON serialization with macro-derived type classes |
| **serde-proto** (`otavia-serde-proto`) | Protocol Buffers serialization (in progress) |
| **sql** + **sql-mysql-driver** / **sql-postgres-driver** | SQL abstraction with MySQL and PostgreSQL drivers |
| **log4a** (`otavia-log4a`) | Asynchronous logging framework |
| **testkit** (`otavia-testkit`) | Testing utilities for Actors |

External ecosystem: protocol codecs ported from Netty and [Eclipse Vert.x](https://vertx.io/) — [otavia-projects](https://github.com/otavia-projects).

## Get Started

Add the dependency:

```scala
// sbt
libraryDependencies += "cc.otavia" %% "otavia-all" % "@MAVEN@"

// Mill
ivy"cc.otavia::otavia-all:@MAVEN@"

// Maven
// <groupId>cc.otavia</groupId> <artifactId>otavia-all_3</artifactId>
```

Then read the [Quick Start](./docs/_docs/quick_start.md) guide.

## Build from Source

Requires JDK 17+. Uses [Mill](https://mill-build.com/).

```bash
./mill __.compile    # compile all modules
./mill __.test       # run all tests
./mill core.test     # test a single module
```

See [CLAUDE.md](./CLAUDE.md) for architecture details and development guidance.

## Contributing

Any contributions are welcome!

## License

[Apache License 2.0](./LICENSE)
