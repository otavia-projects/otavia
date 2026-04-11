---
layout: main
title: Core Concepts and Design
---

## Challenges of existing programming paradigms

As Moore's Law fades and the size of modern software systems grows exponentially, single-core CPUs and even standalone computers are no longer sufficient to meet our needs. Modern software systems must not only work well with multi-core CPUs on a single machine, but also run on multiple computers in a distributed manner. These challenges are seriously impacting the current mainstream programming paradigm.

The first challenge is asynchronous programming: combining the object-oriented paradigm with asynchronous programming often produces callback hell. This approach destroys object-oriented encapsulation and makes the code logic more diffuse, reducing maintainability.

The second is that functional programming has become more popular. Functional programming presents a beautiful vision: everything is immutable! This makes code secure and easier to test. However, functional programming has some drawbacks: it is not easy to deal with state, and real software systems often need to manage large amounts of state, IO, and other side effects. Although functional programming offers techniques such as `Monad` and `Effect` to address these scenarios, these techniques have a steep learning curve for many developers.

Meanwhile, an older programming paradigm exists that seems better suited to the complexity of modern, highly concurrent, distributed software systems: the Actor model, proposed in 1973.

`otavia` is an Actor programming framework implemented in `Scala 3`. We aim to explore ways to integrate the Actor model more effectively with modern programming languages and to address some of the shortcomings of the traditional Actor model.

## Design principles of otavia

Object-oriented programming is a very useful programming paradigm. But the crisis came with multithreading and multiple CPU cores: multithreading is like a herd of savage bulls rampaging through a fragile jungle of objects! You must carefully consider whether each object will be accessed by more than one thread at the same time, and thoroughly verify your program's concurrency safety. However, this is not easy to achieve.

Happily, new technologies have emerged to solve these problems. The most popular technical solutions are coroutines and JVM virtual threads. However, while these techniques effectively alleviate low CPU utilization caused by suspended threads, they do not address the fundamental need to carefully design objects for concurrency safety.

We believe the main reason this problem has become so severe is that mainstream programming languages and object-oriented paradigms lack key features: the ability to organize concurrency and the ability to organize execution flow.

Organizing concurrency and execution flow is where the Actor model excels! With the advent of `Scala 3`, we saw the possibility of designing an Actor programming framework that better aligns with object-oriented thinking. After a long period of conceptualization, `otavia` and its associated toolkit were designed.

We believe that a better programming hierarchy would look like the following, and the design of `otavia` follows this hierarchy:

**System > Process > Threads > Virtual Threads/Stacked coroutines > `Actor > Objects > Functions`**

In this hierarchical design, the Actor is the end point of concurrency, i.e., the Actor and its internal components should all run single-threaded. Actors communicate by sending messages, and multiple messages are processed one by one in the Actor's mailbox in a single-threaded manner. The logic inside the Actor can be either object-oriented or functional, or even a combination of both, as in `Scala`. Now everything is simple again — you can use objects without worrying about concurrent access.

## Core Runtime

The basic unit for managing concurrency in `otavia` is the `Actor`. To meet real-world needs including IO programming and timed tasks, `otavia` adds several components to the Actor model. The core components that make up the `otavia` runtime are:

![](../_assets/images/programming_model.drawio.svg)

- **Actor**: The basic unit of concurrency. Actors communicate via messages. Two fundamental subclasses exist: `StateActor` (for business logic) and `ChannelsActor` (for IO management). See [Actor Model](guide/actor_model.md).
- **ActorSystem**: Container for Actor instances, responsible for creating Actors, managing their lifecycle, and scheduling their execution. See [Threading Model](guide/threading_model.md).
- **Message**: Three basic types: `Notice` (fire-and-forget), `Ask[R <: Reply]` (request-response), and `Reply`. Compile-time type safety is enforced via `Actor[+M <: Call]` and `Address[-M <: Call]`. See [Message Model](guide/message_model.md).
- **Stack**: The carrier that manages message execution within an Actor. `Stack` uses a state machine with `StackState` and `Future/Promise` chains to handle asynchronous message flows without callbacks. See [Stack Execution Model](guide/stack_model.md).
- **Address**: Actors are isolated and can only communicate via `Address`. `PhysicalAddress` routes directly to an Actor's mailbox; `RobinAddress` provides round-robin load balancing with thread affinity. See [Address Model](guide/address_model.md).
- **Channel**: Represents an IO object (file, network connection, etc.), ported from Netty. Uses `ChannelPipeline` for byte-level processing and `Inflight` for request-response multiplexing. See [Channel Pipeline](guide/channel_pipeline.md) and [IO Model](guide/io_model.md).
- **Reactor**: The IO execution layer within each `ActorThread`. Each thread owns an `IoHandler` (e.g., `NioHandler` wrapping a NIO `Selector`) that performs NIO select, read, and write. Pluggable via SPI. See [Reactor Model](guide/reactor_model.md).
- **Timer**: Generates timeout events using a `HashedWheelTimer`. Supports actor timeouts, channel timeouts, ask timeouts, and resource timeouts.
- **IoC**: Compile-time type-safe dependency injection for Actor addresses using Scala 3 match types. See [Actor IOC](guide/ioc.md).

To program with `otavia`, the user must first start an instance of `ActorSystem`, which represents the `otavia` runtime. The `ActorSystem` contains a pool of `ActorThread`s, each with its own `IoHandler` and `Timer` component. Next, the user starts their own `Actor` instance using `ActorSystem`. The `ActorSystem` returns the `Address` of the newly created `Actor` instance to the caller. The user can then send messages to the Actor via this `Address`. Unlike method calls in object-oriented programming, sending a message does not directly execute the logic that processes the message; instead, it puts the message into the `Actor`'s mailbox and waits for it to be processed.

![](../_assets/images/actor_instance.drawio.svg)

## Ecosystem

Beyond the core runtime, `otavia` contains an extensive ecosystem of modules. For details, see [otavia ecosystem](https://github.com/otavia-projects):

- **CPS transformation** ([otavia-async](https://github.com/otavia-projects/otavia-async)): `async/await` syntax based on Scala 3 metaprogramming.
- **Buffer**: High-performance buffer management ported from Netty, featuring `AdaptiveBuffer` as a replacement for `CompositeBuffer`.
- **Codec**: Common `ChannelHandler` abstractions (`Byte2ByteXXcoder`, `Byte2MessageDecoder`, `Message2ByteEncoder`, `Message2MessageXXcoder`).
- **Serde**: A unified serialization/deserialization framework that works directly with `Buffer` for zero-copy performance. See [Serde Framework](guide/serde.md).
- **SQL**: A standard API for Actor access to relational databases, referencing the JDBC design.
- **SLF4A**: Asynchronous logging framework referencing SLF4J design. See [SLF4A](guide/slf4a.md).
- **Testkit**: Testing utilities for Actors.
