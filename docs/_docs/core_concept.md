---
layout: main
title: Core Concept
---

## Challenges of existing programming paradigm

With the gradual failure of Moore's Law and the crazy growth of the scale of modern software systems, single-core CPUs
and even single machines can no longer meet the requirements well. In order to cope with this challenge, modern software
systems not only need to make good use of multi-core CPUs on a single machine, but even need to distribute a system to
run on multiple computers to complete the business. These challenges seriously impact the current mainstream programming
paradigm, and also make the current mainstream programming paradigm evolve in a new direction.

The first is asynchronous programming, but the combination of the object-oriented programming paradigm and asynchronous
often produces callback hell, an approach that destroys object-oriented encapsulation, but also code logic is more
fragmented, making the software less maintainable.

The second is that functional programming has become much more popular, and now basically all major programming
languages support some functional features. Functional programming presents a beautiful vision: everything is immutable,
which makes writing code like defining a mathematical function, and once the function is defined, every time it is
called, as long as the input is the same then the output is the same, and there is no behavior beyond that. This
programming paradigm is a wonderful idea, and code written in this way is not only secure but also easier to test, and
the behavior of the code is more controllable. But functional programming also has a fatal drawback: it's not easy to
deal with state, and a real software system often needs to deal with a lot of state, IO, and so on. Although functional
programming also has Monad, Effect and other components to deal with state, but these techniques are difficult for many
developers to understand, and the learning cost is very high.

At the same time, there is an old programming paradigm that seems to be better suited to handle such complex and highly
concurrent distributed software systems. That is the Actor model, proposed in 1973. However, the Actor model also had
some drawbacks that prevented it from becoming as popular as object-oriented programming.

`otavia` is an implementation of an Actor programming model based on `Scala 3`, and aims to explore a way to integrate
the Actor model more effectively with modern programming languages, and also to explore ways to address some of the
shortcomings of the traditional Actor model. The following article provides an overview of the core concepts and design
of `otavia`.

## Core Runtime

## Actor

## Timer