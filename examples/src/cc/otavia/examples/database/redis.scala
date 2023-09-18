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

package cc.otavia.examples.database

import cc.otavia.core.actor.ChannelsActor.{Connect, ConnectReply}
import cc.otavia.core.actor.{MainActor, MessageOf}
import cc.otavia.core.address.Address
import cc.otavia.core.stack.StackState.FutureState
import cc.otavia.core.stack.{NoticeStack, StackState}
import cc.otavia.core.system.ActorSystem
import cc.otavia.redis.Client
import cc.otavia.redis.cmd.{Auth, OK}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

private class MainRedis(host: String, port: Int, password: String, database: Int)
    extends MainActor(Array(host, port.toString, password, database.toString)) {

    private var client: Address[MessageOf[Client]] = _

    override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] =
        stack.stackState match
            case StackState.start =>
                client = system.buildActor(() => new Client())
                val state = new FutureState[ConnectReply]()
                client.ask(Connect(new InetSocketAddress(host, port)), state.future)
                state.suspend()
            case state: FutureState[ConnectReply] if state.replyType.runtimeClass.equals(classOf[ConnectReply]) =>
                logger.info("redis connected")
                val state = new FutureState[OK]()
                client.ask(Auth(password), state.future)
                state.suspend()
            case state: FutureState[OK] =>
                if (state.future.isFailed) state.future.causeUnsafe.printStackTrace()
                stack.`return`()

}

@main def redis(host: String, port: Int, password: String, database: Int): Unit =
    val system = ActorSystem()
    system.buildActor(() => new MainRedis(host, port, password, database))
