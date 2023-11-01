---
layout: main
title: 核心概念
---

## 现有编程模型的挑战

随着摩尔定律的逐渐失效与现代软件系统规模的疯狂增长，单核CPU甚至单机已经不能很好的满足要求。为了应对这种挑战，现代软件系统不仅
需要在单机上很好运用多核CPU，甚至还需要将一个系统分布运行在多台计算机上完成业务。这些挑战严重的冲击着目前的主流编程范式，也让
目前主流编程范式向新的方向进化。

第一是异步编程，但是面向对象编程范式和异步结合的时候往往产生回调地狱，这种方法破坏了面向对象的封装性，而且也是代码逻辑更加分散，
使软件变得不容易维护。

第二是函数式编程变得更加流行，目前基本主流的编程语言都支持部分函数式特性了。函数式编程提出了一个美好的愿景：一切都是不可变的，
这使得编写代码就像定义一个数学函数，一旦函数完成定义，每次调用时只要输入一致那么输出也一致，除此之外不会有任何其余行为。这种
编程范式是一种很美妙的思想，采用这种方式编写的代码不仅安全而且更加容易测试，代码的行为也更可控。但是函数式也有一个致命的缺点：
那就是不容易处理状态，而一个现实的软件系统往往需要处理很多状态、IO等。虽然函数式编程也有Monad、Effect等组件来处理状态，但是
这些技术对于很多开发者来说难以理解，而且学习成本很高。

与此同时，有一个古老的编程范式似乎更加适合处理这种复杂的高并发分布式的软件系统。这就是1973提出的Actor模型。但是Actor模型
也同样存在一些缺点使其并没有向面向对象编程那样大规模流行起来。

`otavia` 是基于 `Scala 3` 实现的一个Actor编程模型，并且希望探索出一条Actor模型与现代编程语言进行更加有效结合的路径，
也希望探索出一些方法解决传统Actor模型的一些缺陷。文章以下内容将概括式的介绍 `otavia` 中的核心概念与设计。

## otavia 的设计哲学

面向对象编程是一种非常有用的编程思想，虽然原始的面向对象思想有点类似于Actor模型，即对象等价于Actor，但是由于直接的方法调用
的代价远远小于消息传递，现代主流的编程语言对面向对象的实现都将其退化为了一种代码组织与封装的方式。

## 核心运行时

`otavia` 中并发与资源的基本单元为 `Actor`, 对于大多数场景来说，用户只需要实现各种自定义的 `Actor` 然后通过 `Actor` 之间
的消息交互来实现整体的系统功能。但是在现有的编程基础设施中，要做到理想的 `Actor` 编程模型并不简单，因为真实的编程场景不只是程
序内部组件的交互，还涉及与系统外部的其他组件进行交互，而这又涉及 IO 编程，甚至还涉及定时任务。为了满足这些需求，`otavia` 在
原始Actor模型的基础上增加了一些新的组件。

构成 `otavia` 运行时的核心组件是：

- `Actor`: `Actor` 是 `otavia` 中并发与资源的基本单元，其有两个基本子类。`Actor` 之间通过发送消息进行通信。
    * `StateActor`：普通 `Actor` 。
    * `ChannelsActor`： 用于处理 IO 的 `Actor` 。
- `ActorSystem`: `Actor` 实例容器，负责创建`Actor` 实例、管理 `Actor` 实例的生命周期，和调度 `Actor` 实例的执行。
- `Timer`: 产生定时事件，生成超时 `Event` 并且发送给相关组件。
- `Reactor`: 处理 IO 传输，并且把 IO 结果以 `Event` 的方式传输给 `ChannelsActor`。
- `Address`: Actor实例之间相互隔离，他们之间通过 `Address` 进行消息的发送。

![](../../_assets/images/programming_model.drawio.svg)

使用 `otavia` 编程，用户必须先启动一个 `ActorSystem` 实例，这个实例代表了 `otavia` 的运行时。这个实例里面包含了调度
Actor 实例运行的线程池，`Timer` 组件、`Reactor` 组件。接下来用户只需要使用 `ActorSystem` 启动自己的 `Actor` 实例，
`ActorSystem` 会向调用着返回对应 `Actor` 实例的地址。接下来，用户就可以通过这个 `Address` 向 Actor 发送消息了。
一旦 Actor 接收到消息，`ActorSystem` 就会分配一个线程来执行这个 Actor 实例。

![](../../_assets/images/actor_instance.drawio.svg)

另一种 Actor 实例获得线程执行的条件是收到 `Event`。 在 `otavia` 中 `Event` 由 `Timer` 和 `Reactor` 产生， 用户
编程 时只需要关心由 `Timer` 产生的 `Event`， 处理由 `Reactor` 产生的 `Event` 由 `ChannelsActor` 进行了进一步的
封装。

## Actor

`otavia` 中有两类基本的 `Actor` ，`StateActor` 和 `ChannelsActor`， 用户可以根据自己的需要选择继承其中的一种。

![](../../_assets/images/two_types_actor.drawio.svg)

`StateActor`: 普通 Actor ， 用户可以实现这种 Actor 来管理状态，发送、接收消息。这种 Actor 还可以与 `Timer` 进行交互
用于注册一个超时事件，当超时事件触发的时候， `Timer` 发向 Actor 发送 `Event`， 然后 `ActorSystem` 调度 Actor 执行
以处理 `Event`。

`ChannelsActor`：

### Types of ChannelsActor

![](../../_assets/images/actor_type.drawio.svg)

### Channel

![](../../_assets/images/architecture_of_channel.drawio.svg)

#### Pipeline

![](../../_assets/images/pipeline.drawio.svg)

#### Channel Inflight

![](../../_assets/images/channel_inflight.drawio.svg)

## 消息模型

![](../../_assets/images/message_types.drawio.svg)

## 事件

## Stack

![](../../_assets/images/stack_resume.drawio.svg)

## Timer

##                         