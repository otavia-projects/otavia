---
layout: main
---

<div align=center>
<img src="../_assets/images/logo.drawio.svg" alt="otavia" >
</div>

<h1 align=center><b>otavia</b></h1>

<p align=center ><b>A super fast IO & Actor programming model</b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)
![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3-blue)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/cc.otavia/otavia-runtime_3?server=https%3A%2F%2Fs01.oss.sonatype.org)

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

## Project modules

### core modules

- `otavia-buffer`: A zero-deps buffer implementation for replace `java.nio.ByteBuffer` forked from Netty.
- `otavia-serde`: A generic serialization and deserialization framework based on `buffer`.
- `otavia-runtime`: Core Actor model, IO model(channel), slf4a, reactor model, IOC, message model, and thread model.
- `otavia-codec`: Some generic ChannelHandler.
- `otavia-handler`: Some special ChannelHandler, eg, SSL, io traffic control, timeout.
- `otavia-async`: A implementation of `async/await` transformation for Actor message sending/waiting based on CPS(
  Continuation
  Passing Style) transformation powered by metaprogramming of Scala 3.
- `otavia-sql`: Actor database connect specification for RDBMS.
- `otavia-sql-macro`: Derivation for class use in `otavia-sql`.

### serde ecosystem

- `otavia-serde-json`: JSON serialization and deserialization.
- `otavia-json-macro`: Macro for `otavia-serde-json`.
- `otavia-serde-http`: HTTP serialization and deserialization.
- `otavia-http-macro`: Macro for `otavia-serde-http`.
- `otavia-serde-proto`: Protocol buffer serialization and deserialization.
- `otavia-proto-macro`: Macro for `otavia-serde-proto`.

### channel codec ecosystem

- `otavia-codec-haproxy`: Haproxy ChannelHandlers and Actors.
- `otavia-codec-http`: HTTP ChannelHandlers and Actors.
- `otavia-codec-memcache`: Memcache ChannelHandlers and Actors.
- `otavia-codec-mqtt`: MQTT ChannelHandlers and Actors.
- `otavia-codec-redis`: Redis ChannelHandlers and Actors.
- `otavia-codec-smtp`: SMTP ChannelHandlers and Actors.
- `otavia-codec-socks`: SOCKS4/5 ChannelHandlers and Actors.
- `otavia-codec-kafka`: KAFKA ChannelHandlers and Actors.
- `otavia-codec-dns`: DNS ChannelHandlers and Actors.

### database drivers

- `otavia-mysql-driver`: mysql driver for otavia.
- `otavia-postgres-driver`: postgres driver for otavia.

### slf4a logger ecosystem

- `otavia-log4a`: A simple slf4a logger implementation.

### web framework

- `sponge`: A simple web framework based on otavia ecosystem.

### io transport

- `otavia-native-transport`: a native io transport via JNI.

## Project plans

| version | plan                                                                                                                                                         |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0.2.*   | `otavia-buffer` completed.                                                                                                                                   |
| 0.3.*   | `otavia-runtime` runnable. complete first runnable `otavia-runtime` with NIO transport, Actor model, message model, thread model, IOC, slf4a, reactor model. |
| 0.4.*   | `otavia-serde-json`, `otavia-serde-http`, `otavia-codec`, `otavia-codec-http` can work.                                                                      |
| 0.5.*   | Design `otavia-sql`, and a demo Mysql driver implementation.                                                                                                 |
| 0.\*.*  | Implement `otavia-codec-*`, `otavia-handler`, `otavia-serde-*`.                                                                                              |
| 0.\*.*  | More RDBMS drivers based on `otavia-sql`.                                                                                                                    |
| 0.\*.*  | Implement `otavia-async`.                                                                                                                                    |
| 0.\*.*  | Higher test coverage.                                                                                                                                        |
| 1.0.*   | Stabilize core API and core module API. Production ready!                                                                                                    |

> The Future: version 2.0
> 1. remote actor: support send/receive message to/from remote actor.
> 2. auto distribution: hot move actor instance to cluster actor system based on Actor community detection and
     make the smallest cost of distribution.

