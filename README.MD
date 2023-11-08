<div align=center>
<img src="docs/_assets/images/logo.drawio.svg" alt="otavia" >
</div>
<h1 align=center>otavia</h1>

<p align=center ><b>Your shiny new IO & Actor programming model!</b></p>

![GitHub](https://img.shields.io/github/license/yankun1992/otavia)
[![GitHub Pages](https://github.com/otavia-projects/otavia/actions/workflows/gh-pages.yml/badge.svg)](https://otavia-projects.github.io/otavia/home.html)
![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3-blue)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/cc.otavia/otavia-runtime_3?server=https%3A%2F%2Fs01.oss.sonatype.org)

> This project is under incubating, the API is not stable now, don't use it in production.

<hr>

Language: [简体中文](./README.zh_cn.md)

<hr>

## Introduction

`otavia` is an IO and Actor programming model power by Scala 3, it provides a toolkit to make writing high-performance
concurrent programs more easily.

You can learn more about `otavia` in the following document.

- [Quick Start](./docs/_docs/quick_start.md)
- [Core Concept and Design](./docs/_docs/core_concept.md)

More document can be found at [website](https://otavia-projects.github.io/otavia/home.html)

## Features

- **full-link asynchronous**：Everything is asynchronous, no blocking, no thread suspending.
- **Forget threads, forget locks**：With `otavia` programming, you will no longer be plagued by multithreading problems;
  everything you write runs in a single thread!
- **Simpler Concurrent**: Actors and Channel let you build systems that scale up, using the resources of a server more
  efficiently, and out.
- **Resilient by Design**: Building on the principles of The Reactive Manifesto Otavia allows you to write systems that
  self-heal and stay responsive in the face of failures.
- **High Performance**: build Millions actor instance and send many billion message in seconds.
- **Type Safe**: Message send between actor is type safe in compile time.
- **Zero-Cost ask-pattern**: Send ask message and get reply message like call a method, but zero-cost.
- **IOC of Actor**: The actor system is also see as a IOC container, user can autowire actor by actor type.
- **Powerful IO Stack**: The IO stack is fork from [Netty](https://netty.io), but support AIO and file channel.
- **Async and Await**: Async send ask message and await to continue.
- **Open Ecosystem**: Otavia provides a module mechanism that allows users to easily use third-party module libraries.

## Contributes

Any contributions are welcome!