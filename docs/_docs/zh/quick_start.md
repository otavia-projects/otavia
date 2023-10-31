---
layout: main
title: 快速开始
---

## 环境要求

![Static Badge](https://img.shields.io/badge/JDK-17%2B-blue)
![Static Badge](https://img.shields.io/badge/Scala-3.3%2B-blue)

`otavia` 虽然主要运行在JVM平台上，但是为了保证可靠的编译时类型安全，目前只支持 `Scala 3`, 如果您对 `Scala 3`
目前不是很熟悉，您可以参考以下资料进行学习。

- 基础知识（对于学习 `otavia` 足够了）: [Scala 3 Book](https://docs.scala-lang.org/zh-cn/scala3/book/introduction.html)
- 高级知识：[Scala 3 Language Reference](https://docs.scala-lang.org/scala3/reference/)

以下所有示例的源码可以在 [otavia-examples](https://github.com/otavia-projects/otavia-examples) 中找到。

## 添加依赖

如果您使用 sbt , 请添加以下依赖：

```scala
libraryDependencies += "cc.otavia" %% "otavia-runtime" % "{version}"
```

如果您使用 mill：

```scala
ivy"cc.otavia::otavia-runtime:{version}"
```

如果使用 maven:

```text
<dependency>
    <groupId>cc.otavia</groupId>
    <artifactId>otavia-runtime</artifactId>
    <version>{version}</version>
</dependency>
```

## 简单的 Ping-Pong Actors

这个简单的示例定义了两个 `Actor`: `PingActor` 和 `PongActor`, `PingActor` 接收 `Start` 消息，并且向 `PongActor `
发送 `Ping` 消息， 每个发送的 `Ping` 消息都必须对应一个 `Pong` 回复消息。

### 定义消息

根据以上描述，我们需要3种消息，这3种消息分别代表3种不同角色的消息，这也是 `otavia` 3种基本的消息类型。`Start`
消息是一种 `Notice` 消息， `Notice` 消息是 `otavia` 中一种不需要获得回复的消息，只要有相关 `Actor` 的地址，
您就可以向 `Actor` 发送 `Notice` 消息；`Ping` 是一种 `Ask` 消息，这种消息必须对应一种回复消息，如果一个 `Actor` 向
其他 `Actor` 发送了这种消息，就意味着他必须收到一个对应的回复消息（有点像方法定义中的方法参数）；`Pong` 是一种回复消息，
回复消息有点像方法定义中的返回值。

`Start` 消息是 `Notice` 类型，所以必须继承 `Notice` trait

```scala
case class Start(sid: Int) extends Notice
```

`Pong` 必须继承 `Reply` trait, `Ping` 是 `Ask` 类型的消息，必须继承 `Ask` trait, `Ask` trait 带有一个类型约束，
用来描述这个 `Ask` 消息期望获得的回复的消息类型

```scala
case class Pong(pingId: Int) extends Reply

case class Ping(id: Int) extends Ask[Pong]
```

### 实现 Actor

有了消息之后，我们来定义我们 Actor。

首先我们确定我们的 `Actor` 能接收的消息类型，因为 `otavia` 是一种消息类型安全的 `Actor` 编程框架，所以我们先来确定每种
`Actor` 能接收的消息类型： `PingActor` 能接收 `Start` 消息和 `Pong` 消息，`PongActor` 接收 `Ping` 消息并且回复
`Pong` 消息。因为在 `otavia` 中回复消息通过 `Ask` 消息进行约束，所以在 `Actor` 的定义中就不需要对这种消息进行约束，由
于 `PingActor` 需要给 `PongActor` 发送消息，所以 `PingActor` 需要知道 `PongActor` 的地址。 大概能定义出我们
的Actor的类名及泛型参数如下：

```scala
final class PongActor() extends StateActor[Ping] {
  // ...
}

final class PingActor(pongActorAddress: Address[Ping]) extends StateActor[Start] {
  // ...
}
```

这里出现了 `StateActor` 我们暂时可以先不用管，`otavia` 中的最终 `Actor` 必须继承 `StateActor` 或 `ChannelsActor`，
`ChannelsActor` 是用于处理 IO 的 `Actor` , 其余所有的 `Actor` 都是 `StateActor`。

接下来让我们来实现具体的消息处理吧！

首先是 `PingActor` , 他需要处理 `Start` 消息，并且处理过程中需要发送 `Ping` 消息，然后等待 `Pong` 回复消息，然后结束
`Start` 消息的处理。

```scala
final class PingActor(pongActorAddress: Address[Ping]) extends StateActor[Start] {
  override def continueNotice(stack: NoticeStack[Start]): Option[StackState] = stack.state match {
    case _: StartState =>
      println("PingActor handle Start message")
      println("PingActor send Ping Message")
      val state = FutureState(1)
      pongActorAddress.ask(Ping(stack.notice.sid), state.future)
      state.suspend()
    case state: FutureState[Pong] if state.id == 1 =>
      val future = state.future
      if (future.isSuccess) {
        println(s"PingActor received ${future.getNow} message success!")
        assert(future.getNow.pingId == stack.ask.sid)
      }
      stack.`return`()
  }
}
```

`continueNotice` 是 `Actor` 处理 `Notice` 消息的入口，从其他地方发送来的 `Notice` 消息都会从这个方法传入 `Actor`,
接下来我们来实现 `PongActor`, `PongActor` 接收 `Ping` 这种 `Ask` 消息，然后回复一个 `Pong` `Reply` 消息：

```scala
final class PongActor() extends StateActor[Ping] {
  override def continueAsk(stack: AskStack[Ping]): Option[StackState] = {
    println(s"PongActor received ${stack.ask} message")
    println(s"PongActor reply ${stack.ask} with Pong message")
    stack.`return`(Pong(stack.ask.id))
  }
}
```

`continueAsk` 是 `Actor` 处理 `Ask` 消息的入口，从其他地方发送来的 `Ask` 消息都会从这个方法传入 `Actor`。

我们可以发现，处理消息 `continueXXX` 的方法并不是直接处理消息，而是将消息装入了 `Stack` 中，`Notice` 消息装入
`NoticeStack` 中，`Ask` 消息装入 `AskStack` 中。在 `otavia` 中，为了方便管理消息的依赖关系和发送 `Reply`
消息，引入了 `Stack` 这种数据结构，引入了 `Future` 来接收 `Ask` 消息的返回 `Reply` 消息（注意这里的 `Future` 不是 scala
标准库的 `Future`） ，为了等待 `Future` 达到可执行状态，引入了 `StackState` ， 一个 `StackState` 可以关联一个或者多个
`Future` , 只有当 `StackState` 的 `resumable` 方法为 `ture` 或者关联的所有的 `Future` 都达到完成状态的时候，这个
`Stack` 才可以继续被调度执行，`continueXXX` 每次执行的时候从一个状态开始，结束的时候返回 `Option[StackState]`，
`return` 方法用于结束 `Stack`, 如果是 `AskStack`，`return` 方法用于发送 `AskStack` 中 `Ask` 消息的返回 `Reply` 消息。

![](../../_assets/images/stack_resume.drawio.svg)

至此，我们需要的所有的 `Actor` 和消息都已经完全实现了。接下来，启动一个 `ActorSystem` 来运行这些 `Actor` 吧

### 运行 actor

```scala
@main def run(): Unit = {
  val system = ActorSystem()
  val pongActor = system.buildActor(() => new PongActor())
  val pingActor = system.buildActor(() => new PingActor(pongActor))

  pingActor.notice(Start(88))
}
```

通过 `ActorSystem()` 我们就可以轻松创建一个 `ActorSystem`, `ActorSystem` 是 `otavia` 中 actor 的运行时容器， 一个 JVM 实例
只允许启动一个 `ActorSystem` 实例。通过 `ActorSystem` 的 `buildActor` 方法，我们可以实例化我们定义的 actor ， `buildActor`
方法并不会返回 actor 实例对象本身，相反他返回的是一个地址，我们可以通过这个地址发送对应 actor 能处理的消息。

以上的一切都是编译时类型安全的，如果您向 `buildActor` 返回的地址发送对应 actor 不能处理的消息，这将不能通过编译。如果您使用
`AskStack` 的 `return` 方法返回与对应 `Ask` 消息不匹配的 `Reply` 消息，这也将不能通过编译。

## 接收多种类型消息的 Actor

以上的示例演示了处理一种消息类型的 actor， 但是在真实的场景中我们往往需要在一个 actor 中处理多种类型的消息。这在 `Scala 3`
中非常简单，而且由于 `Scala 3` 强大的 `Union Types` 和 `Intersection Types`，我们还可以做到处理多种消息的编译时类型安全。

假如我们需要实现一个 actor， 这个 actor 需要处理的消息为：接收 `Hello` 消息并且返回 `World` 消息， 接收 `Ping` 消息并且
返回 `Pong` 消息， 接收 `Echo` 消息并且不返回任何消息。

以上需求需要我们定义如下几种消息：

```scala
case class Echo() extends Notice

case class World() extends Reply

case class Hello() extends Ask[World]

case class Pong() extends Reply

case class Ping() extends Ask[Pong]
```

然后来实现我们的 actor：

```scala
final class MultiMsgActor() extends StateActor[Echo | Hello | Ping] {

  override def continueNotice(stack: NoticeStack[Echo]): Option[StackState] = {
    println("MultiMsgActor received Echo message")
    stack.`return`()
  }

  override def continueAsk(stack: AskStack[Hello | Ping]): Option[StackState] = {
    stack match {
      case stack: AskStack[Hello] if stack.ask.isInstanceOf[Hello] => handleHello(stack)
      case stack: AskStack[Ping] if stack.ask.isInstanceOf[Ping] => handlePing(stack)
    }
  }

  private def handleHello(stack: AskStack[Hello]): Option[StackState] = {
    println("MultiMsgActor received Hello message")
    stack.`return`(World())
  }

  private def handlePing(stack: AskStack[Ping]): Option[StackState] = {
    println("MultiMsgActor received Ping message")
    stack.`return`(Pong())
  }
}
```

## 计时

`otavia` 运行时包含了一个强大的计时器组件 `Timer` ，您可以使用多种方式与 `Timer` 进行交互，以下将介绍主要的使用场景：

### 处理注册的定时事件

Actor 有方法处理注册的定时事件，其定义为

```scala
 protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {}
```

如果定时事件触发，`Timer` 将会发送 `TimeoutEvent` 事件给 actor 实例，最终超时事件将会通过 `handleActorTimeout` 方法传入
actor 中

```scala
final class TickActor() extends StateActor[Nothing] { // [Nothing] if no message need process!

  private var onceTickId: Long = 0
  private var periodTickId: Long = 0

  override protected def afterMount(): Unit = {
    onceTickId = timer.registerActorTimeout(TimeoutTrigger.DelayTime(1, TimeUnit.SECONDS), self)
    periodTickId = timer.registerActorTimeout(TimeoutTrigger.DelayPeriod(2, 2, TimeUnit.SECONDS, TimeUnit.SECONDS), self)
  }

  override protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {
    if (timeoutEvent.registerId == periodTickId) {
      println(s"period timeout event triggered at ${LocalDateTime.now()}")
    } else if (timeoutEvent.registerId == onceTickId) {
      println(s"once timeout event triggered at ${LocalDateTime.now()}")
    } else {
      println("Never run this")
    }
  }
}
```

### Stack Sleep

如果我们想让一个 `Stack` 等待一段时间才开始执行，我们可以让 `StackState` 关联到一个 `TimeoutEventFuture` , 这个 `Future`
会将超时事件作为结果。只有收到超时事件的时候，`TimeoutEventFuture` 才完成。

在之前的示例中，`PingActor` 处理 `Start` 消息的时候使用了 `FutureState` , 这是 `otavia` 定义的一些比较常用的状态类，如果
这些状态类不能满足您的需要，您也可以自定义 `StackState`。

让我们改造一下之前的 `PingActor`，现在我们要求这个Actor收到 `Start` 消息之后发送 `Ping` 请求，然后收到 `Pong` 消息的时候
同时需要等待2秒才能继续调度这个 `NoticeStack` 执行。

现在 `FutureState` 已经不能满足我们的需求了，因为其只绑定了一个 `ReplyFuture`, 现在我们不仅需要绑定 `ReplyFuture`，还需要
绑定 `TimeoutEventFuture`，只有这两种 `Future` 都完成的时候才开始执行这个 `Stack`。让我们来定义我们新的 `StackState` 吧。

```scala
class PongTimeoutState extends StackState {
  val pongFuture = ReplyFuture[Pong]()
  val timeoutFuture = TimeoutEventFuture()
}
```

接下来，重新实现我们的 `PingActor`

```scala
final class PingActor(pongActorAddress: Address[Ping]) extends StateActor[Start] {
  override def continueNotice(stack: NoticeStack[Start]): Option[StackState] = stack.state match {
    case _: StartState =>
      println("PingActor handle Start message")
      println("PingActor send Ping Message")
      val state = PongTimeoutState()
      pongActorAddress.ask(Ping(stack.notice.sid), state.pongFuture)
      timer.sleepStack(state.timeoutFuture, 2 * 1000)
      state.suspend()
    case state: PongTimeoutState =>
      val future = state.pongFuture
      if (future.isSuccess) {
        println(s"PingActor received ${future.getNow} message success!")
        assert(future.getNow.pingId == stack.ask.sid)
      }
      stack.`return`()
  }
}
```

好了，完成了！现在我们的 `PingActor` 即使收到 `Pong` 回复消息，也要等待2秒这个 `Stack` 才会被继续调度执行。

### 给 Reply 消息设置超时

有时候，当我们发送一个 `Ask` 消息，对面的 Actor 可能会耗时比较久，但是我们不想让我们的请求 Actor 等待太久，这种情况我们可以
怎么办呢？也许聪明的您已经想到答案了！`Stack` 怎么样到达可执行状态呢，之前我们讲过：
> 一个 `StackState` 可以关联一个或者多个 `Future` , 只有当 `StackState` 的 `resumable` 方法为 `ture` 或者关联的所
> 有的 `Future` 都达到完成状态的时候，这个 `Stack` 才可以继续被调度执行。

所以我们可以重载 `StackState` 的 `resumable` 方法！ 现在来重新定义一下我们 `PongTimeoutState`

```scala
class PongTimeoutState extends StackState {
  val pongFuture = ReplyFuture[Pong]()
  val timeoutFuture = TimeoutEventFuture()

  override def resumable(): Boolean = timeoutFuture.isDone
}
```

现在只要 `timeoutFuture` 完成，那么 `Stack` 就会被继续调度执行了。接下来我们只需检测我们的 `pongFuture.isDone` 判断
是否完成。

这的确是一种方法，但是考虑到这种超时需求比较常见，`otavia` 提供了更简单的方法，我们只需对最开始的 `PingActor` 进行一点点改动：

```scala
final class PingActor(pongActorAddress: Address[Ping]) extends StateActor[Start] {
  override def continueNotice(stack: NoticeStack[Start]): Option[StackState] = stack.state match {
    case _: StartState =>
      println("PingActor handle Start message")
      println("PingActor send Ping Message")
      val state = FutureState(1)
      pongActorAddress.ask(Ping(stack.notice.sid), state.future, 2 * 1000)
      state.suspend()
    case state: FutureState[Pong] if state.id == 1 =>
      val future = state.future
      if (future.isSuccess) {
        println(s"PingActor received ${future.getNow} message success!")
        assert(future.getNow.pingId == stack.ask.sid)
      }
      stack.`return`()
  }
}
```

注意到不同的地方了吗！`ask` 方法自带了一个超时参数!

```scala
pongActorAddress.ask(Ping(stack.notice.sid), state.future, 2 * 1000) // new 
pongActorAddress.ask(Ping(stack.notice.sid), state.future) // old
```

如果2秒后仍然没有收到 `Pong` 消息，那么这个 `Future` 将会被设置成完成状态，但是与之前不同的是我们不能从 `Future` 里面拿到
`Pong` 消息了，他的状态已经是失败了，即 `isDone=ture isSuccess=false isFailed=ture`。因为这个 `StackState` 只关联
了一个 `Future`，而这个 `Future` 已经是完成状态了，所以这个 `Stack` 就可以继续被调度执行了。

## Actor 的生命周期

在 `otavia` 中，用户不用花太多心思管理 actor 的生命周期，actor 实例仍然被 JVM 垃圾回收管理，只要这个 actor 没有地址引用他，那么这个
actor 实例将被 JVM 的垃圾回收系统自动回收。

Actor 里有如下几种方法可以在不同的生命周期过程中调用

- `afterMount`:
- `beforeRestart`:
- `restart`:
- `afterRestart`:
- `AutoCleanable.clean`:

![](../../_assets/images/actor_life_cycle.drawio.svg)




















