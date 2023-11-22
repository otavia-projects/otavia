---
layout: main
---

<div align=center>
<img src="../_assets/images/logo.drawio.svg" alt="otavia" >
</div>

<h1 align=center><b>otavia</b></h1>

<p align=center ><b>Your shiny new IO & Actor programming model! </b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)
![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3-blue)
![Maven Central](https://img.shields.io/maven-central/v/cc.otavia/otavia-runtime_3)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/cc.otavia/otavia-runtime_3?server=https%3A%2F%2Fs01.oss.sonatype.org)

## Introduction

`otavia` is an IO and Actor programming model power by Scala 3, it provides a toolkit to make writing high-performance
concurrent programs more easily.

You can get a quick overview of the basic usage and core design of `otavia` in the following documentation:

- [Quick Start](./quick_start.md)
- [Core Concepts and Design](./core_concept.md)

You can also learn about other modules in `otavia` through other documentation on this site.

## Features at a Glance

- **Full-Link Asynchronous**：Everything is asynchronous, no blocking, no thread suspending.
- **Forget Threads, Forget Locks**：You will no longer be plagued by multithreading problems; everything you write runs
  in a single thread!
- **Simpler Concurrent**: Actors and Channel let you build systems that scale up, using the resources of a server more
  efficiently, and out.
- **Resilient by Design**: Building on the principles of The Reactive Manifesto Otavia allows you to write systems that
  self-heal and stay responsive in the face of failures.
- **High Performance**: build Millions actor instance and send many billion message in seconds.
- **Type Safe**: Message send between actor is type safe in compile time.
- **Zero-Cost Ask-Pattern**: Send ask message and get reply message like call a method, but zero-cost.
- **DI of Actor**: An `ActorSystem` is also seen as a container for `Actor` instances, and developers can type-safely
  inject dependent `Actor`s at compile time.
- **Powerful IO Stack**: The IO stack is ported from [Netty](https://netty.io), but support AIO and file channel.
- **async/await**: Implement a set of `async/await` syntaxes based on the CPS (Continuation Passing Style)
  using `Scala 3` metaprogramming tools.
- **Simple threading model**: The threading model of the `otavia` runtime is very simple and efficient, allowing you to
  maximize the utilization of your system's CPU!
- **Zero-Deps**: The core modules does not depend on any third-party packages.
- **Open Ecosystem**: Otavia provides a module mechanism that allows users to easily use third-party module libraries.

## Ecosystem

The IO stack of `otavia` is ported from Netty. In order to make the IO tasks work better with the Actor model, `otavia`
is not fully compatible with Netty. Happily, most of the application-layer network protocol codecs in the Netty
ecosystem can be easily ported to `otavia`, so we maintain this ecosystem project to port various application-layer
network protocol codecs from Netty and [Eclipse Vert.x](https://vertx.io/).

- [GitHub - otavia-projects](https://github.com/otavia-projects)

## Contributes

Any contributions are welcome!