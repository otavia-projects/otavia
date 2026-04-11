---
layout: main
title: 详细指南
---

# 详细指南

本节为 otavia 运行时的每个核心组件提供详细指南。

## Actor 系统

- [Actor 模型](actor_model.md) - Actor 层次、生命周期、邮箱调度、Barrier 机制
- [消息模型](message_model.md) - 消息类型、Envelope、类型安全、Event
- [Stack 执行模型](stack_model.md) - Stack 状态机、Future/Promise 链、对象池化
- [Address 模型](address_model.md) - Address 路由、PhysicalAddress、RobinAddress、线程亲和

## IO 系统

- [Channel Pipeline](channel_pipeline.md) - Handler 链、inbound/outbound 传播、executionMask
- [IO 模型](io_model.md) - Channel inflight、Barrier 流控、读/写/Accept 生命周期
- [Reactor 模型](reactor_model.md) - NIO 传输层、Selector 引擎、SPI 机制

## 运行时

- [线程模型](threading_model.md) - ActorThread 循环、HouseManager 调度、工作窃取
- [Actor IoC](ioc.md) - BeanManager、依赖注入、autowire
- [Serde 框架](serde.md) - 与 Buffer 集成的序列化/反序列化
- [SLF4A](slf4a.md) - 异步日志框架
