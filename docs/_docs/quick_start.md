---
layout: main
title: Quick Start
---

## Environment

| environment | version |
|-------------|---------|
| JDK         | 17+     |
| Scala       | 3.3+    |

Although otavia mainly runs on the JVM platform, it currently only supports `Scala 3` in order to ensure reliable
compile-time type safety, so if you're not familiar with `Scala 3` at the moment, you can learn about it with the
following information.

- basic(enough for otavia): [Scala 3 Book](https://docs.scala-lang.org/scala3/book/introduction.html)
- advance: [Scala 3 Language Reference](https://docs.scala-lang.org/scala3/reference/)

The source code for all the following examples can be found
at [otavia-examples](https://github.com/otavia-projects/otavia-examples).

## Add dependency

If you use sbt, add the dependency:

```scala
libraryDependencies += "cc.otavia" %% "otavia-runtime" % "{version}"
```

If you use mill:

```scala
ivy"cc.otavia::otavia-runtime:{version}"
```

if maven:

```xml

<dependency>
    <groupId>cc.otavia</groupId>
    <artifactId>otavia-runtime</artifactId>
    <version>{version}</version>
</dependency>
```

## Simple Ping-Pong Actors

This simple example defines two Actors: `PingActor` and `PongActor`. `PingActor` receives the `Start` message and sends
a `Ping` message to the `PongActor`. Each `Ping` message sent must correspond to a `Pong` reply message.

### Defining Messages

According to the above description, we need three kinds of messages, each of which represents three different roles,
which are also the three basic types of messages in `otavia`: `Start` is a `Notice` message, which is a kind of message
in otavia that does not need to get a reply, as long as you have the address of the `Actor` in question, you can send
the message to it. `Ping` is an `Ask` message, which must correspond to a `Reply` message, so if an Actor sends this
message to another Actor, it means that it must receive a corresponding `Reply` message (kind of like a method parameter
in a method definition); and `Pong` is a `Reply` message, which is kind of like a return value in a method definition.

The `Start` message is of type `Notice`, so it must inherit the `Notice` trait.

```scala
case class Start(sid: Int) extends Notice
```

`Pong` must inherit the `Reply` trait, `Ping` is a message of type `Ask` and must inherit the `Ask` trait, the `Ask`
trait carries a type constraint that describes the type of message for which a reply is expected for this `Ask` message.

```scala
case class Pong(pingId: Int) extends Reply

case class Ping(id: Int) extends Ask[Pong]
```

### Implementing the actor

Once we have our messages, let's define our Actor.

First let's determine the types of messages our `Actor` can receive, since `otavia` is a message-type-safe `Actor`
programming framework, so let's determine the types of messages that each `Actor` can receive: `PingActor`
receives `Start` and `Pong` messages, and `PongActor` receives `Ping` messages and replies to `Pong` messages.
Since reply messages are constrained by `Ask` messages in `otavia`, there is no need to constrain such messages in the
definition of `Actor`, and since `PingActor` needs to send a message to `PongActor`, `PingActor` needs to know the
address of `PongActor`. Roughly, we can define the class name and generic parameters of our Actor as follows:

```scala
final class PongActor() extends StateActor[Ping] {
  // ...
}

final class PingActor(pongActorAddress: Address[Ping]) extends StateActor[Start] {
  // ...
}
```

Here comes `StateActor` which we can ignore for now, the final `Actor` in `otavia` must inherit either `StateActor`
or `ChannelsActor`, `ChannelsActor` is the `Actor` that is used to deal with IO, and all the rest of the `Actor`s
are `StateActor`.

Next, let's implement the specific message processing!

First, there is the `PingActor` , which needs to process the `Start` message, and during the process, it needs to send
a `Ping` message, then wait for `Pong` to reply to the message, and then end the processing of the `Start` message.

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

`continueNotice` is the entry point for `Actor` to process a `Notice` message. `Notice` messages sent from elsewhere
will be passed into `Actor` from this method, and we're going to implement `PongActor` next. `PongActor` receives `Ping`
this kind of `Ask` message, and then replies with a `Pong` `Reply` message:

```scala
final class PongActor() extends StateActor[Ping] {
  override def continueAsk(stack: AskStack[Ping]): Option[StackState] = {
    println(s"PongActor received ${stack.ask} message")
    println(s"PongActor reply ${stack.ask} with Pong message")
    stack.`return`(Pong(stack.ask.id))
  }
}
```

`continueAsk` is the entry point for `Actor` to process `Ask` messages, and `Ask` messages sent from elsewhere are
passed into `Actor` from this method.

We can see that the method that handles the message `continueXXX` does not process the message directly, but loads the
message into the `Stack`, the `Notice` message into the `NoticeStack`, and the `Ask` message into the `AskStack`.
In `otavia`, the `Stack` data structure is introduced to facilitate the management of message dependencies and the
sending of `Reply` messages; A `Future` is introduced to receive the return `Reply` message from an `Ask` message (note
that the `Future` here is not the `Future` of the scala standard library); To wait for a `Future` to reach an executable
state, a `StackState` is introduced. A `StackState` can be associated with one or more `Future`s, and a `Stack` can only
be scheduled if the `resumable` method of the `StackState` is `ture`, or if all associated `Futures` have reached
completion. The `continueXXX` method starts at a state and returns `Option[StackState]` at the end of each execution.
The `return` method is used to terminate a `Stack`: in the case of an `AskStack`, the `return` method is used to send
the return `Reply` message of the `Ask` message in the `AskStack`.

![](../../_assets/images/stack_resume.drawio.svg)

At this point, all the `Actors` and messages we need are fully implemented. Next, start an `ActorSystem` to run
these `Actors`.

### Running the actor

```scala
@main def run(): Unit = {
  val system = ActorSystem()
  val pongActor = system.buildActor(() => new PongActor())
  val pingActor = system.buildActor(() => new PingActor(pongActor))

  pingActor.notice(Start(88))
}
```

With `ActorSystem()` we can easily create an `ActorSystem`, which is a runtime container for the actor in `otavia`. A
JVM instance is only allowed to start one `ActorSystem` instance. The `buildActor` method of `ActorSystem` allows us to
instantiate our defined actor. The `buildActor` method does not return the actor instance object itself, instead it
returns an address to which we can send messages that the actor can handle.

Everything above is compile-time type-safe; if you send a message to the address returned by `buildActor` that the
corresponding actor can't handle, it won't compile. If you use the AskStack`'s `return` method to return a `
Reply` message that does not match the corresponding `Ask` message, this will also fail to compile.

## Actor that receives multiple types of messages

The above example demonstrates an actor that handles one type of message, but in real-world scenarios we often need to
handle multiple types of messages in a single actor. This is very easy in `Scala 3`, and thanks to `Scala 3`'s
powerful `Union Types` and `Intersection Types`, we can also make it compile-time type-safe to handle multiple messages.

Suppose we need to implement an actor that receives a `Hello` message and returns a `World` message, receives a `Ping`
message and returns a `Pong` message, and receives an `Echo` message and returns no message.

The above requirement requires us to define the following kinds of messages:

```scala
case class Echo() extends Notice

case class World() extends Reply

case class Hello() extends Ask[World]

case class Pong() extends Reply

case class Ping() extends Ask[Pong]
```

Then we implement our actor:

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

## Timer

## LifeCycle of Actor

In `otavia`, users don't have to spend much time managing the life cycle of an actor. The actor instance is still
managed by the JVM garbage collection, and as long as the actor has no address that references it, the actor instance
will be automatically recycled by the JVM's garbage collection system.

There are several methods in Actor that can be called during different lifecycle processes

- `afterMount`:
- `beforeRestart`:
- `restart`:
- `afterRestart`:
- `AutoCleanable.clean`:

![](../../_assets/images/actor_life_cycle.drawio.svg)


