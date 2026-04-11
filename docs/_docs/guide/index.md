---
layout: main
title: Guide
---

# Guide

This section provides detailed guides for each core component of the otavia runtime.

## Actor System

- [Actor Model](actor_model.md) - Actor hierarchy, lifecycle, mailbox dispatch, barrier mechanism
- [Message Model](message_model.md) - Message types, envelopes, type safety, events
- [Stack Execution Model](stack_model.md) - Stack state machine, Future/Promise chains, object pooling
- [Address Model](address_model.md) - Address routing, PhysicalAddress, RobinAddress, thread affinity

## IO System

- [Channel Pipeline](channel_pipeline.md) - Handler chain, inbound/outbound propagation, executionMask
- [IO Model](io_model.md) - Channel inflight, barrier flow control, read/write/accept lifecycles
- [Reactor Model](reactor_model.md) - NIO transport, Selector engine, SPI mechanism

## Runtime

- [Threading Model](threading_model.md) - ActorThread loop, HouseManager scheduling, work stealing
- [Actor IOC](ioc.md) - BeanManager, dependency injection, autowire
- [Serde Framework](serde.md) - Serialization/deserialization with Buffer integration
- [SLF4A](slf4a.md) - Asynchronous logging framework
