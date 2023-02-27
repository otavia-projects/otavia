/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.otavia.redis

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.*
import io.otavia.core.message.*
import io.otavia.core.reactor.Event
import io.otavia.core.stack.*
import io.otavia.redis.Client.{Connect, Connected, ConnectingState}

import java.nio.channels.{SelectionKey, SocketChannel}

//class Client extends ChannelsActor[Client.MSG] {
//
//  private val channels           = collection.mutable.HashMap.empty[Long, Channel]
//  private var connected: Boolean = false
//
//  override def continueAsk(state: Client.MSG & Ask[?] | AskFrame): Option[StackState] = state match
//    case connect: Client.Connect =>
//      val connectingState = new ConnectingState(connect.poolSize)
//      for (elem <- (0 until connect.poolSize)) {
//        val channel: Channel = ??? // SocketChannel.open() // how to create pipeline
//        channels.put(channel.id, channel)
//        // connect register resume
////        channel.connect(connect.host, connect.port, connectingState.waiters(elem)) // how to resume
//      }
//      Some(connectingState)
//    case askFrame: AskFrame =>
//      askFrame.state match
//        case state: ConnectingState =>
//          if (state.waiters.forall(!_.isException)) {
//            connected = true
//            askFrame.`return`(Connected())
//          } else askFrame.`return`(ExceptionMessage(state.waiters.find(_.isException).get.exception))
//        case _ => None
//    case _ => None
//
//  override def continueChannelMessage(msg: AnyRef | ChannelFrame): Option[StackState] = ???
//
//  override def init(channel: Channel): Unit = ???
//
//  override def close(): Unit = ???
//}

object Client {
    type MSG = Connect | Select

    final case class Connect(host: String, port: Int, username: String, password: String, poolSize: Int)(using
        IdAllocator
    ) extends Ask[Connected]
    final case class Connected() extends Reply

    final case class Select(index: Int)          extends Ask[SelectReply]
    final case class SelectReply(status: String) extends Reply

    private final class ConnectingState(size: Int) extends StackState {
        val waiters                       = new Array[ChannelReplyWaiter[Unit]](size)
        override def resumable(): Boolean = waiters.forall(_.received)
    }

}
