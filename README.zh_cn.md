<div align=center>
<img src="docs/_assets/images/logo.drawio.svg" alt="otavia" >
</div>
<h1 align=center>otavia</h1>

<p align=center ><b>一个有趣的 IO & Actor 编程模型</b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)
![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3-blue)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/cc.otavia/otavia-runtime_3?server=https%3A%2F%2Fs01.oss.sonatype.org)


> 这个项目目前处于孵化状态， API 设计还不稳定，请不要用于生产环境中。

<hr>

Language: [English](./README.md)

<hr>

## 介绍

`otavia` 是一个基于 `Scala 3` 的 `IO` 和 `Actor` 编程模型，他提供了一系列工具集使编写高性能并发程序变得非常容易。

你可以通过以下文档更加详细的了解 `otavia`

- [快速入门](./docs/_docs/zh/quick_start.md)
- [核心概念与设计](./docs/_docs/zh/core_concept.md)

更多文档可以在项目 [网站](https://otavia-projects.github.io/otavia/home.html) 查看。

## 特性

- **全链路异步**: 一切都是异步的，没有阻塞，没有线程挂起。
- **忘记线程、忘记锁**：使用 `otavia` 编程，你将不再会被多线程问题困扰了，你编写的一切代码都是单线程运行的！
- **简化并发**: `Actor` 和 `Channel` 让您更加容易构建高性能、易伸缩及更低资源占用的的系统。
- **设计的弹性**： 基于《反应性宣言》的原则，`Otavia` 允许你编写能自我修复的系统，并在面对失败时保持反应。
- **高性能**：在几秒钟内创建百万 `Actor` 实例并发送数亿条消息。
- **类型安全**： `Actor` 之间的发送的消息在编译时是类型安全的。
- **零成本 ask-pattern**：使用 ask 模式发送消息然后接收回复消息就像调用普通方法一样，而且开销非常低。
- **IOC**： `ActorSystem` 也被看作是一个 `Actor` 实例的容器，开发者可以通过 `Actor` 类型自动注入。
- **强大的IO栈**： IO 栈是由 [Netty](https://netty.io) 分叉而来，但支持 `AIO` 和文件通道。
- **异步和等待**： 通过 `Scala 3` 元编程支持消息的 `async` 和 `await` 语义。
- **开放的生态系统**： `Otavia` 提供了一个模块机制，允许用户轻松使用第三方模块库。

## 贡献

欢迎任何形式的贡献！

