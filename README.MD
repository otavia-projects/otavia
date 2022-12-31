<div align=center>
<img src="logo.drawio.svg" alt="otavia" >
</div>
<h1 align=center>otavia</h1>

<p align=center ><b>A super fast IO & Actor programming model</b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)

<hr>

Language: [简体中文](./README.zn_ch.md)

<hr>

> The first runnable version is developing.

## Programming Model

In `otavia`, developer only focus on

- `ActorBase`: 资源与代码执行的基本单元，`ActorBase` 实例之间的不能直接访问对方资源，只能通过 `Message`
  进行通信，其有两个基本子类 `Actor` 和 `ChannelGroup`。
- `Actor`: 基本执行单元，负责接收消息和发送消息，用户需要根据自己的业务逻辑实现自己的 `Actor`。
- `ChannelGroup`: 基本执行单元，并且管理一组 `socket` 的生命周期，负责将接收到的消息编码传输给
  对应 `socket` 及从 `socket` 读取数据解码出消息然后发送给其他 `ActorBase` 实例。其可以向 `Eventor`
  注册 `socket` 关心的 IO 事件和定时事件。注册的事件到达条件时 `Eventor` 发送 `Event` 事件，
  `ChannelGroup` 除了处理消息之外，还要处理 `Eventor` 发送来的 `Event` 事件。
- `Address`: `ActorBase` 实例的消息发送客户端，`ActorBase` 内部不能直接访问其他 `ActorBase` 实例，
  但是可以通过 `Address` 给其代表的 `ActorBase` 实例或实例集合发送消息。
- `Eventor`: 事件监听器，监视注册的 `socket` 事件和定时事件，并产生 `Event` 然后发送给相关的
  `ChannelGroup` 实例。
- `Message`: 消息，`ActorBase` 实例之间通信的基本单位，不可变。
- `Event`: `socket` 的 IO 就绪事件和定时事件。
- `ActorSystem`: `ActorBase` 实例容器，负责创建`ActorBase` 实例、管理 `ActorBase` 实例的生命周期，
  和调度 `ActorBase` 实例的执行。

## Features

- Message type safe: the message type should indicate when the `ActorBase` is defined.
- dependency inject:

## Quick Start

sbt

```scala
libraryDependencies += "io.otavia" %% "core" % "{version}"
```

mill

```scala

```

maven

```xml

<dependency>
    <groupId>io.otavia</groupId>
    <artifactId>core</artifactId>
    <version>{version}</version>
</dependency>
```

## Contributes
