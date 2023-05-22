<div align=center>
<img src="docs/_assets/images/logo.drawio.svg" alt="otavia" >
</div>
<h1 align=center>otavia</h1>

<p align=center ><b>A super fast IO & Actor programming model</b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)

> This project is under incubating, the API is not stable now, don't use it in production.

<hr>

Language: [简体中文](./README.zh_cn.md)

More document can be find at [website](https://otavia-projects.github.io/otavia/home.html)

<hr>

## Introduction

[Otavia](https://otavia-projects.github.io/otavia/home.html) is an IO and Actor programming model power by Scala 3, it
provides a toolkit to make writing high-performance concurrent programs more easily.

## Features

- **Simpler Concurrent**: Actors and Channel let you build systems that scale up, using the resources of a server more
  efficiently, and out.
- **Resilient by Design**: Building on the principles of The Reactive Manifesto Otavia allows you to write systems that
  self-heal and stay responsive in the face of failures.
- **High Performance**: build Millions actor instance and send billion message in seconds.
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

## Quick Start

### Add dependency

sbt

```scala
libraryDependencies += "io.github.otavia-projects" %% "core" % "{version}"
```

mill

```scala
ivy"io.github.otavia-projects:core:{version}"
```

maven

```xml

<dependency>
    <groupId>io.github.otavia-projects</groupId>
    <artifactId>core</artifactId>
    <version>{version}</version>
</dependency>
```

### Basic examples

See document to learn the [tutorials](https://otavia-projects.github.io/otavia/docs/quick_start/index.html).

## Contributes

Any contributions is wellcome!