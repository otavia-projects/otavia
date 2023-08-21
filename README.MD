<div align=center>
<img src="docs/_assets/images/logo.drawio.svg" alt="otavia" >
</div>
<h1 align=center>otavia</h1>

<p align=center ><b>A super fast IO & Actor programming model</b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)
![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3-blue)

> This project is under incubating, the API is not stable now, don't use it in production.

<hr>

Language: [简体中文](./README.zh_cn.md)

More document can be found at [website](https://otavia-projects.github.io/otavia/home.html)

<hr>

## Introduction

[Otavia](https://otavia-projects.github.io/otavia/home.html) is an IO and Actor programming model power by Scala 3, it
provides a toolkit to make writing high-performance concurrent programs more easily.

## Features

- **Simpler Concurrent**: Actors and Channel let you build systems that scale up, using the resources of a server more
  efficiently, and out.
- **Resilient by Design**: Building on the principles of The Reactive Manifesto Otavia allows you to write systems that
  self-heal and stay responsive in the face of failures.
- **High Performance**: build Millions actor instance and send many billion message in seconds.
- **Type Safe**: Message send between actor is type safe in compile time.
- **IOC of Actor**: The actor system is also see as a IOC container, user can autowire actor by actor type.
- **Powerful IO Stack**: The IO stack is fork from [Netty](https://netty.io), but support AIO and file channel.
- **Async and Await**: Async send ask message and await to continue.
- **Open Ecosystem**: Otavia provides a module mechanism that allows users to easily use third-party module libraries.

## Programming Model

In `otavia`, developer only focus on

- `Actor`: The basic unit of resource and code execution, `Actor` instances cannot access each other's resources
  directly, they can only communicate with each other through `Message`. `Actor` has two basic subclasses `StateActor`
  and `ChannelsActor`, user-implemented `Actor` must inherit from one of these two classes.
- `Message`: messages, `Actor`s communicate with each other via `message`, immutable.
- `Address`: The client to which the message of an `Actor` instance is sent. `Actor` cannot access other `Actor`
  instances directly, but can only send `message` to the `Actor` instance or collection of instances it represents
  via `Address`.
- `Event`: Events, `Actor` can register events of interest to `Reactor` and `Timer`, and when an event occurs, `Reactor`
  and `Timer` send `Event` to `Actor`.
- `StateActor`: The basic execution unit, responsible for receiving messages and sending messages, users need to
  implement their own `Actor` according to their business logic, and can register timeout events with `Timer`.
- `ChannelsActor`: basic execution unit and manages the life cycle of a set of `Channel`s, responsible for encoding and
  transmitting incoming messages to the corresponding `Channel` and reading data from the `Channel` to decode the
  message and send it to other `Actor` instances. It can register IO events of interest to the `Channel` with
  the `Reactor`. When the registered events reach the conditions `Reactor` sends `Event` events.
- `Reactor`: IO event listener that monitors registered `Channel` events and generates `Event` which is then sent to the
  relevant `ChannelsActor` instance.
- `Timer`: Timeout event generator that sends `Event` to the corresponding `Actor`.
- `ActorSystem`: `Actor` instance container, responsible for creating `Actor` instances, managing the life cycle
  of `Actor` instances and scheduling the execution of ready `Actor` instances.

## Main modules

### core modules

| module name | description                                                                                                                                                                       | coverage | docs |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|------|
| buffer      | A zero-deps buffer implementation for replace `java.nio.ByteBuffer` forked from Netty.                                                                                            |          |      |
| serde       | A generic serialization and deserialization framework based on `buffer`.                                                                                                          |          |      |
| core        | Core Actor model, IO model(channel), slf4a, reactor model, IOC, message model, and thread model.                                                                                  |          |      |
| codec       | Some generic ChannelHandler.                                                                                                                                                      |          |      |
| handler     | Some special ChannelHandler, eg, SSL, io traffic control, timeout.                                                                                                                |          |      |
| async       | A implementation of `async/await` transformation for Actor message sending/waiting based on CPS(Continuation Passing Style) transformation powered by metaprogramming of Scala 3. |          |      |

### serde ecosystem

| module name       | description                                        | coverage | docs |
|-------------------|----------------------------------------------------|----------|------|
| serde-json        | JSON serialization and deserialization.            |          |      |
| serde-json-macro  | Macro for `serde-json`.                            |          |      |
| serde-http        | HTTP serialization and deserialization.            |          |      |
| serde-proto       | Protocol buffer serialization and deserialization. |          |      |
| serde-proto-macro | Macro for `serde-proto`.                           |          |      |

### channel codec ecosystem

| module name    | description                                            | coverage | docs |
|----------------|--------------------------------------------------------|----------|------|
| codec-adbc     | Actor database connect (adbc) specification for RDBMS. |          |      |
| codec-haproxy  | Haproxy ChannelHandlers and Actors.                    |          |      |
| codec-http     | HTTP ChannelHandlers and Actors.                       |          |      |
| codec-memcache | Memcache ChannelHandlers and Actors.                   |          |      |
| codec-mqtt     | MQTT ChannelHandlers and Actors.                       |          |      |
| codec-redis    | Redis ChannelHandlers and Actors.                      |          |      |
| codec-smtp     | SMTP ChannelHandlers and Actors.                       |          |      |
| codec-socks    | SOCKS4/5 ChannelHandlers and Actors.                   |          |      |
| codec-kafka    | KAFKA ChannelHandlers and Actors.                      |          |      |

### logger ecosystem

| module name | description                           | coverage | docs |
|-------------|---------------------------------------|----------|------|
| log4a       | A simple slf4a logger implementation. |          |      |

### web framework

| module name | description             | coverage | docs |
|-------------|-------------------------|----------|------|
| web         | A simple web framework. |          |      |

## Project plans

| version | plan                                                                                                                                     |
|---------|------------------------------------------------------------------------------------------------------------------------------------------|
| 0.2.*   | `buffer` completed.                                                                                                                      |
| 0.3.*   | `core` runnable. complete first runnable `core` with NIO transport, Actor model, message model, thread model, IOC, slf4a, reactor model. |
| 0.4.*   | `serde-json`, `serde-http`, `codec`, `codec-http` can work.                                                                              |
| 0.5.*   | Design `codec-adbc`, and a demo Mysql driver implementation.                                                                             |
| 0.\*.*  | Implement `codec-*`, `handler`, `serde-*`.                                                                                               |
| 0.\*.*  | More RDBMS drivers based on `codec-adbc`.                                                                                                |
| 0.\*.*  | Implement `async`.                                                                                                                       |
| 0.\*.*  | Higher test coverage.                                                                                                                    |
| 1.0.*   | Stabilize core API and core module API. Production ready!                                                                                |

> The Future: version 2.0
> 1. remote actor: support send/receive message to/from remote actor.
> 2. auto distribution: hot move actor instance to cluster actor system based on Actor community detection and
     make the smallest cost of distribution.

## Quick Start

### Add dependency

sbt

```scala
libraryDependencies += "cc.otavia" %% "core" % "{version}"
```

mill

```scala
ivy"cc.otavia::core:{version}"
```

maven

```xml

<dependency>
    <groupId>cc.otavia</groupId>
    <artifactId>core</artifactId>
    <version>{version}</version>
</dependency>
```

### Basic examples

See document to learn the [tutorials](https://otavia-projects.github.io/otavia/docs/quick_start/index.html).

## Contributes

Any contributions are welcome!