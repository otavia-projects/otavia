---
layout: main
title: 核心概念
---

## 现有编程模型的挑战

随着摩尔定律的逐渐失效与现代软件系统规模的疯狂增长，单核 CPU 甚至单机已经不再满足我们的要求了。为了应对这种挑战，现代软件系统不仅
需要在单机上很好的使用多核 CPU，甚至还需要将一个系统分布运行在多台计算机上完成业务。这些挑战严重的冲击着目前的主流编程范式，也让
目前主流编程语言向新的方向进化。

第一是异步编程，但是面向对象编程范式和异步结合的时候往往产生回调地狱，这种方法破坏了面向对象的封装性，而且也让代码逻辑更加分散，
使软件变得不容易维护。

第二是函数式编程变得更加流行，目前基本主流的编程语言都支持部分函数式特性了。函数式编程提出了一个美好的愿景：一切都是不可变的！
这使得编写代码就像定义一个数学函数，一旦函数完成定义，每次调用时只要输入一致那么输出也一致，除此之外不会有任何其余行为。这种
编程范式是一种很美妙的思想，采用这种方式编写的代码不仅安全而且更加容易测试，代码的行为也更可控。但是函数式也有一些的缺点：
那就是不容易处理状态，而一个现实的软件系统往往需要处理很多状态、IO 等。虽然函数式编程也有 `Monad`、`Effect` 等技术来应对
这些场景，但是这些技术对于很多开发者来说太复杂了，而且学习成本比较高。还记得那句名言么？
> Monad 不过是自函子范畴上的一个幺半群嘛，有啥难以理解的？

与此同时，有一个古老的编程范式似乎更加适合处理现代这种复杂的高并发分布式的软件系统。那就是1973提出的 Actor 模型。但是 Actor
模型同样也存在一些缺点，或许这也是其并没有像面向对象编程那样大规模流行起来的原因。

`otavia` 是基于 `Scala 3` 实现的一个 Actor 编程模型，并且希望探索出一条 Actor 模型与现代编程语言进行更加有效结合的路径，
也希望探索出一些方法解决传统 Actor 模型的一些缺陷。文章以下内容将概括式的介绍 `otavia` 中的核心概念与设计。

## otavia 的设计哲学

面向对象编程是一种非常有用的编程思想，虽然原始的面向对象思想有点类似于 Actor 模型，即对象等价于 Actor，但是由于直接调用方法
的代价远远小于消息传递，现代主流的编程语言对面向对象的实现都将其退化为了一种代码组织与封装的方式。这在单线程的情况下很美好，
一切都被组织得井井有条。

但是危机发生在多线程多 CPU 核心之后：多线程就像一群野蛮的公牛一样在脆弱的对象丛林里横冲直撞！现在程序的执行路径不在单一，不再被
对象井井有条的管控。相反，现在你需要仔仔细细关心每一个对象是否会被多个线程同时进入，你需要仔仔细细检查程序的并发安全问题。但是
这并不容易做到。特别是对于一个规模巨大的软件系统，你不知道有多少线程在你的对象丛林里冲撞，所以你需要去人肉模拟每一个线程的运行
路径，追踪他们的运行路径，然后找出哪些对象应该需要处理并发安全问题。想象一下，你要追踪这群丛林里的野蛮公牛，一个一个细致的检查
他们踩到了哪些草丛！天啊，这想一想就让人发疯。这种工作已经让人疲惫不堪了，但是还有更加严重的问题：这些公牛踩到某些草丛的时候会
突然静止！他们被阻塞从而被挂起了，这降低了系统CPU的使用率，而这可能又会导致启动更多的线程，并发竞争越来越严重，直到系统在一个
没有被严格检查的并发问题下崩溃！

值得高兴的是目前已经出现了一些新的技术来解决这些问题。目前如日中天的技术方案便是协程、JVM 虚拟线程。但是在我看来，这些技术有效
的缓解了线程被挂起从而导致 CPU 使用率低的问题，但是并发竞争导致我们必须仔细设计我们的对象的问题并没有缓解。

问题变得这么严重我们认为主要的原因就是目前主流编程语言和面向对象编程范式缺失了一部分关键的特性！即缺乏对并发进行组织，缺乏对执行
流的组织！而目前并发和执行流是和对象耦合在一起的，这在早期的单线程环境中没有什么问题，但是现在多线程多 CPU 环境下，这些问题就
变得非常严重了！

组织并发和执行流，正是 Actor 模型擅长的领域！而 `Scala 3` 的出现，让我们看到了设计出一种更加贴合面向对象思想的 Actor 编程
工具的愿景。于是，在经历了很长一段时间构思之后，我设计了 `otavia` 及其相关工具集。目的是探索一种更加简单、安全的编程范式来应对
当前的挑战。也为了开拓一种新的思路提供给大家来一起推动编程工具的发展！

我认为一个更加好的层级划分体系应该像下面这样，`otavia` 的设计也遵循了这种层级关系

**系统 > 进程 > 线程 > 虚拟线程/有栈协程 > `Actor > 对象 > 函数`**

在这种层级中，Actor 是并发的终点，也就是说，Actor 及其之后的部分都应该是单线程运行的！Actor 之间通过发送消息进行通信，多条消息
会在 Actor 的邮箱中一条一条的以单线程的方式被处理。Actor 内部的逻辑您可以选择面向对象也可以选择函数式，甚至像 `Scala` 一样将
他们结合起来！现在，一切又变得简单了，你可以大胆的使用对象而不必担心那群野蛮的公牛了！

`otavia project` 是一个非常有趣的项目计划，热烈欢迎您的任何贡献！

- [GitHub - otavia-projects/otavia Your shiny new IO & Actor programming model!](https://github.com/otavia-projects/otavia)
- [GitHub - otavia-projects Ecosystem for otavia project.](https://github.com/otavia-projects)

## 核心运行时

`otavia` 中管理并发与资源的基本单元为 `Actor`, 用户只需要实现各种自定义的 `Actor` 然后通过 `Actor`
之间的消息交互来实现整体的系统功能。但是想要做到理想的 `Actor` 编程模型并不简单，因为真实的编程场景不只是程
序内部组件的交互，还涉及与系统外部的其他组件进行交互，而这又涉及 IO 编程，甚至还涉及定时任务。为了满足这些需求，`otavia` 在
原始 Actor 模型的基础上增加了一些新的组件。构成 `otavia` 运行时的核心组件是：

- `Actor`: `Actor` 是 `otavia` 中并发与资源的基本单元，其有两个基本子类。`Actor` 之间通过发送消息进行通信。`Actor`
  还可以通过 `Event` 的方式与 `ActorSystem` 的其他组件进行交互。`Event` 的种类是固定的，用户不能自定义。
    * `StateActor`：普通 `Actor` 。
    * `ChannelsActor`： 用于处理 IO 的 `Actor` 。
- `ActorSystem`: `Actor` 实例容器，负责创建 `Actor` 实例、管理 `Actor` 实例的生命周期，和调度 `Actor` 实例的执行。
- `Timer`: 产生定时事件，生成超时 `Event` 并且发送给相关 `Actor`。
- `Reactor`: 处理 IO 传输，并且把 IO 结果以 `Event` 的方式发送给 `ChannelsActor`。
- `Address`: `Actor` 实例之间相互隔离，他们之间只能通过 `Address` 进行消息的发送。
- `Message`: 用于 `Actor` 之间相互通信，有3种基础类型的消息: `Notice`、`Reply`、`Ask[R <: Reply]`，用户定义的消息必须继承其中的一种或多种。

![](../../_assets/images/programming_model.drawio.svg)

使用 `otavia` 编程，用户必须先启动一个 `ActorSystem` 实例，这个实例代表了 `otavia` 的运行时。`ActorSystem` 里面包含了
调度  `Actor` 实例运行的线程池、`Timer` 组件和 `Reactor` 组件。接下来用户只需要使用 `ActorSystem` 启动自己的 `Actor`
实例。`ActorSystem` 会向调用者返回对应 `Actor` 实例的 `Address`。接下来，用户就可以通过这个 `Address` 向 `Actor` 发送
消息了。与面向对象中调用方法不一样的地方是，发送消息并不会直接执行处理消息的逻辑，而是将消息放入对应 `Actor` 的邮箱中等待处理。
一旦 `Actor` 的邮箱中有可以处理的消息，`ActorSystem` 就会调度一个空闲线程来执行这个 `Actor` 实例。

![](../../_assets/images/actor_instance.drawio.svg)

另一种 `Actor` 实例获得线程执行的条件是收到 `Event`，`Event` 同样也是放入 `Actor` 的邮箱中等待处理。 在 `otavia`
中 `Event` 只能由 `Timer` 和 `Reactor` 产生， 用户编程时只需要关心由 `Timer` 产生的 `TimerEvent`， 处理由 `Reactor`
产生的 `ReactorEvent` 由 `ChannelsActor` 进行了进一步的封装。

## Event

`Event` 是 `Actor` 与 `ActorSystem` 运行时进行交互的基本单元，其种类是固定的，用户不能自定义。`Event` 主要分为两种类型

- `TimerEvent` : 由 `Timer` 产生，用于通知一个超时事件。编程的时候直接会使用到的只有 `TimeoutEvent`, 其余 `TimerEvent`
  用于支持其他超时机制，而且被 `Actor` 进行了封装。
- `ReactorEvent`: 由 `Reactor` 产生，用于通知一个 IO 事件。编程时不用直接处理这种 `Event`, 其被 `ChannelsActor` 进行了封装。

`ActorSystem` 运行时向 `Actor` 实例发送 `Event` 也不会直接调用 `Actor` 实例，而是将 `Event` 放入 `Actor` 实例的邮箱中，然后由
`ActorSystem` 运行时分配空闲的线程调度 `Actor` 实例执行。

## 消息模型

消息是 `Actor` 用来通信的一种特殊对象，建议使用 `case class` 来定义。`otavia` 为了保证消息传输的编译时安全，对消息的类型进行了
分类

![](../../_assets/images/message_types.drawio.svg)

按照消息的用途，`otavia` 将消息分成了两种类型，`Call` 消息是一个请求消息，其用于向 `Actor` 请求一个执行过程，并且期望获得一个返回消息。
`Reply` 就是返回消息。这有点像对象中方法的抽象。我们来看看我们怎么样定义一个方法呢？首先，我们需要给方法起一个名字，然后定义方法的参数，接着
定义方法的返回值类型。在 `otavia` 中 `Call` 代表了方法名和方法参数，`Reply` 代表返回值类型。因为消息发送的代价是大于方法调用的，所以对于
返回值为 `Unit` 的方法，在 `otavia` 中将对应的 `Call` 简化为了 `Notice` ，需要返回值的情况抽象为了 `Ask[R <: Reply]`
，其中 `Notice` 消息是不需要返回 `Reply` 消息的，就像返回值为 `Unit` 的方法没有实际返回值一样。

![](../../_assets/images/message_model.drawio.svg)

`Ask` 是一个带类型参数 `R <: Reply` 的 trait，这个参数用来指定这个 `Ask` 消息期望获取的具体的 `Reply` 消息的类型。所以
`otavia` 中 `Actor` 只需要通过类型参数约束能接收的 `Call` 消息类型就可以做到完全的编译时类型安全。`Actor` 的类型参数为：

```scala
trait Actor[+M <: Call] 
```

## Actor

`otavia` 中有两类基本的 `Actor` ，`StateActor` 和 `ChannelsActor`， 用户可以根据自己的需要选择继承其中的一种。

![](../../_assets/images/two_types_actor.drawio.svg)

`StateActor`: 普通 `Actor` ， 用户可以实现这种 `Actor` 来管理状态，发送、接收消息。这种 `Actor` 还可以与 `Timer` 进行
交互，用于注册一个超时事件。当超时事件触发的时候，`Timer` 会向 `Actor` 发送 `TimeoutEvent`， 然后 `ActorSystem` 调度
`Actor` 执行以处理 `TimeoutEvent`。

`ChannelsActor`：在 `StateActor` 功能的基础之上新增了管理 `Channel` 的功能，`otavia` 中的 `Channel` 从 Netty 移植
而来，也基本跟 Netty 保持一致。但是与 Netty 不同的是，`otavia` 中的 `Channel` 必须跟一个 `ChannelsActor` 绑定，作为
`ChannelsActor` 的一部分进行运行。

为了方便使用，`otavia` 根据不同的场景抽象了以下几种常用的 `Actor`，您可以继承其中的一种来实现您的功能：

![](../../_assets/images/actor_type.drawio.svg)

### Stack

`Stack` 是 `otavia` 中管理 `Actor` 消息的执行的载体。

![](../../_assets/images/stack_resume.drawio.svg)

### 各种 ChannelsActor

为了更好的对各种不同 `Channel` 的管理，`otavia` 实现了几种不同种类的 `ChannelsActor`，他们分别是：

- `AcceptorActor`: 管理 TCP 监听的 `Channel`，其需要实例化一组 `AcceptedWorkerActor`, 对于监听 `Channel` 接受的
  普通 `Channel` 会作为消息发送给其中一个 `AcceptedWorkerActor`, 并且由 `AcceptedWorkerActor` 对接受的 `Channel` 进行管理。
- `AcceptedWorkerActor`: `AcceptorActor` 的工作 `Actor`。
- `SocketChannelsActor`: 管理 TCP 客户端 `Channel`。
- `DatagramChannelsActor`: 管理 UDP `Channel`。

其中所有类型的 `ChannelsActor` 都可以管理文件 `Channel`，如果您只需要使用文件 `Channel`，你可以直接继承 `ChannelsActor`。

## Channel

在 `otavia` 中，一个 `Channel` 就代表一个 IO 对象，比如一个打开的文件、一个网络连接等，因为 `Channel` 是从 Netty 移植而来，
所以基本的组件也跟 Netty 差不多，也有 `ChannelPipeline` `ChannelHandler` `ChannelHandlerContext` 等组件，并且工作
方式也跟 Netty 差不多。

![](../../_assets/images/architecture_of_channel.drawio.svg)

但是为了 `Channel` 更好的与 `otavia` 的 `ChannelsActor` 结合，也需要做出一些调整：

1. Netty 的 `Channel` 创建成功后需要注册到 `EventLoop` 上，`EventLoop` 是一个线程，这个线程会监听 IO 事件，然后调度相关的
   `Channel` 进行执行。同样对于 outbound 调用，也需要转换成任务提交到 `EventLoop` 线程上排队执行。`otavia` 中没有
   `EventLoop`。`otavia` 中的 `Channel` 创建成功后需要挂载到一个 `ChannelsActor` 上，`ChannelsActor` 会将
   `Channel` 中的 `UnsafeChannel` 注册到 `Reactor` 上。`Reactor` 会监听相关 `Channel` 的 IO 事件，然后产生相关的
   `ReactorEvent` 发送给挂载的 `ChannelsActor`，`ChannelsActor` 被调度执行的时候会将 `ReactorEvent`
   分配给相关的 `Channel` 执行 inbound 流程。
2. Netty 的 `EventLoop` 不仅监听IO事件，而且IO事件发生的时候还会调度相关的 `Channel` 进行执行，IO 的监听、IO 数据的读写和
   `ChannelPipeline` 的执行都在同一个 `EventLoop` 线程内。但是 `otavia` 中 IO 的监听、IO 数据的读写由 `Reactor` 负责，
   然后按需生成相关的 `ReactorEvent` 发送给 `ChannelsActor`， 由 `ChannelsActor` 负责调度 `ChannelPipeline` 的执行。
3. Netty 中所有的业务逻辑都必须封装成 `ChannelHandler` 放入 `ChannelPipeline` 中执行，如果一个 inbound 事件到达
   `ChannelPipeline` 尾部 `TailHandler` 仍然没有处理，那么 Netty 会忽略掉这个事件。但是 `otavia` 中 inbound 事件到达
   `TailHandler` 之后会继续传输到到 `Channel` 内的 `Inflight`，然后由 `Inflight` 分发给 `ChannelsActor` 内部的
   `Stack` 处理。事实上，在 `otavia` 中 `ChannelPipeline` 的职责更加集中在字节序列的转换与编解码工作，比如 TLS、压缩、数
   据对象的序列化与反序列化等。其他复杂的业务逻辑直接通过将反序列化的对象传入 `Channel` 的 `Inflight` 组件，然后交给
   `ChannelsActor` 继续处理后续复杂的业务逻辑。

### ChannelPipeline

`ChannelPipeline` 与 Netty 中的 `ChannelPipeline` 基本一致，首尾节点分别是 `HeadHandler` 和 `TailHandler`，当
`Channel` 初始化的时候，用户可以通过 `Channel` 向 `ChannelPipeline` 中添加自定义的 `ChannelHandler`，与 Netty 一样，
`ChannelHandler` 也被 `ChannelHandlerContext` 包裹，然后由 `ChannelHandlerContext` 将所有的 `ChannelHandler` 串
联成一个队列按顺序执行。

`Reactor` 向 `ChannelsActor` 发送来的 `ReactorEvent` 会触发 `ChannelPipeline` 的 inbound 事件，inbound 事件从
`HeadHandler` 向 `TailHandler` 方向传递。调用 `Channel` 的 outbound 相关的方法会触发 outbound 事件从 `TailHandler`
向 `HeadHandler` 方向传递。outbound 事件到达 `HeadHandler` 之后最终会转化为提交到 `Reactor` 的命令。

![](../../_assets/images/pipeline.drawio.svg)

### Channel Inflight

`Inflight` 并不是一个真实存在组件，其只是 `Channel` 中部分数据结构和机制的代表，用户不需要直接与他们交互，只需要通过
`Channel` 的 `setOption` 方法设置一些属性来控制 `Inflight` 的行为。

```scala
// outbound futures which is write to channel and wait channel reply
private val inflightFutures: QueueMap[ChannelPromise] = new QueueMap[ChannelPromise]()
// outbound futures which is waiting channel to send
private val pendingFutures: QueueMap[ChannelPromise] = new QueueMap[ChannelPromise]()
// inbound stack which is running by actor
private val inflightStacks: QueueMap[ChannelStack[?]] = new QueueMap[ChannelStack[?]]()
// inbound stack to wait actor running
private val pendingStacks: QueueMap[ChannelStack[?]] = new QueueMap[ChannelStack[?]]()
```

![](../../_assets/images/channel_inflight.drawio.svg)

`Inflight` 是一种对网络通信数据包发送控制的抽象。我们一般怎么样使用一个网络连接呢？大多数情况是发送一个数据包，然后等待响应
数据包的返回，在响应数据包返回之前，我们不能继续通过这个网络连接发送另外的请求数据包。但是这是一种对网络连接低效的使用方式，
所以有很多应用层协议支持在一个网络连接上连续发送多个请求数据包，比如 HTTP 1.1 中的 pipeline 机制、redis 的 pipeline 机制
等。但是由于这些机制一般实现起来相对复杂，很多应用并没有支持这种优化方式。

在 `otavia` 中，
但是在 `otavia` 中，强大的 `Inflight` 让实现上述功能变得非常简单！在 `Channel` 存在以下几种组件来支持这种对网络连接的高效
使用：

- `inflightFutures`:
- `pendingFutures`:
- `inflightStacks`:
- `pendingStacks`:

你可以通过 `setOption` 来控制这些组件的行为来适应不同的网络连接工作方式：

- `CHANNEL_FUTURE_BARRIER`:
- `CHANNEL_STACK_BARRIER`:
- `CHANNEL_MAX_FUTURE_INFLIGHT`:
- `CHANNEL_MAX_STACK_INFLIGHT`:
- `CHANNEL_STACK_HEAD_OF_LINE`:

## Timer

## 生态系统中其他的核心模块

### CPS变换

### Buffer

### 序列化反序列化框架

### SQL

### 日志

### 测试工具