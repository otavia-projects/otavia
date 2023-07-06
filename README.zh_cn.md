<div align=center>
<img src="docs/_assets/images/logo.drawio.svg" alt="otavia" >
</div>
<h1 align=center>otavia</h1>

<p align=center ><b>一个超快的 IO & Actor 编程模型</b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)
![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3-blue)

> 这个项目目前处于孵化状态， API 设计还不稳定，请不要用于生产环境中。

<hr>

Language: [English](./README.md)

更多文档可以在项目 [网站](https://otavia-projects.github.io/otavia/home.html) 查看。

<hr>

## 介绍

[Otavia](https://otavia-projects.github.io/otavia/home.html) 是一个基于 `Scala 3` 的 `IO` 和 `Actor`
编程模型，他提供了一系列工具集使编写高性能并发程序变得非常容易。

## 特性

- **简化并发**: `Actor` 和 `Channel` 让您更加容易构建高性能、易伸缩及更低资源占用的的系统。
- **设计的弹性**： 基于《反应性宣言》的原则，`Otavia` 允许你编写能自我修复的系统，并在面对失败时保持反应。
- **高性能**：在几秒钟内创建百万 `Actor` 实例并发送数亿条消息。
- **类型安全**： `Actor` 之间的发送的消息在编译时是类型安全的。
- **IOC**： `ActorSystem` 也被看作是一个 `Actor` 实例的容器，开发者可以通过 `Actor` 类型自动注入。
- **强大的IO栈**： IO 栈是由 [Netty](https://netty.io) 分叉而来，但支持 `AIO` 和文件通道。
- **异步和等待**： 通过 `Scala 3` 元编程支持消息的 `async` 和 `await` 语义。
- **开放的生态系统**： `Otavia` 提供了一个模块机制，允许用户轻松使用第三方模块库。

## 编程模型

`otavia` 对于用户编程来说，有以下主要概念

- `Actor`: 资源与代码执行的基本单元，`Actor` 实例之间的不能直接访问对方资源，只能通过 `Message`
  进行通信，其有两个基本子类 `StateActor` 和 `ChannelsActor`，用户实现的 `Actor` 必须继承自这两种类之一。
- `Message`: 消息，`Actor` 之间通过 `message` 相互通信, 不可变。
- `Address`: `Actor` 实例的消息发送的客户端，`Actor` 不能直接访问其他 `Actor` 实例，
  只能通过 `Address` 给其代表的 `Actor` 实例或实例集合发送 `message` 。
- `Event`: 事件，`Actor` 可以向 `Reactor` 和 `Timer` 注册关心的事件，当事件发生时，`Reactor` 和 `Timer` 向 `Actor`
  发送 `Event`。
- `StateActor`: 基本执行单元，负责接收消息和发送消息，用户需要根据自己的业务逻辑实现自己的 `Actor`， 可以向 `Timer` 注册超时事件。
- `ChannelsActor`: 基本执行单元，并且管理一组 `Channel` 的生命周期，负责将接收到的消息编码传输给
  对应 `Channel` 及从 `Channel` 读取数据解码出消息然后发送给其他 `Actor` 实例。其可以向 `Reactor`
  注册 `Channel` 关心的 IO 事件。注册的事件到达条件时 `Reactor` 发送 `Event` 事件。
- `Reactor`: IO 事件监听器，监视注册的 `Channel` 事件，并产生 `Event` 然后发送给相关的 `ChannelsActor` 实例。
- `Timer`: 超时事件发生器，发送 `Event` 给对应的 `Actor`。
- `ActorSystem`: `Actor` 实例容器，负责创建 `Actor` 实例、管理 `Actor` 实例的生命周期，
  和调度就绪的 `Actor` 实例的执行。

## 快速开始

### 添加依赖

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

### 基本示例

查看 [教程](https://otavia-projects.github.io/otavia/docs/quick_start/index.html) 学习。

## 贡献

欢迎任何形式的贡献！

