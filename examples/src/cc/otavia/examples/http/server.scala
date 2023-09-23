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

package cc.otavia.examples.http

import cc.otavia.core.actor.ChannelsActor.{Bind, BindReply}
import cc.otavia.core.actor.MainActor
import cc.otavia.core.slf4a.LoggerFactory
import cc.otavia.core.stack.StackState.FutureState
import cc.otavia.core.stack.{NoticeStack, StackState}
import cc.otavia.core.system.ActorSystem
import cc.otavia.http.server.Router.*
import cc.otavia.http.server.{HttpServer, Router}

import java.nio.charset.StandardCharsets.*
import java.nio.file.Path
import scala.language.unsafeNulls

private class ServerMain() extends MainActor(Array.empty) {

    private val port: Int = 80

    override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] = stack.state match
        case StackState.start =>
            val routers = Seq(static("/statics", Path.of("")), plain("/plaintext", "你好 otavia".getBytes(UTF_8)))
            val server  = system.buildActor(() => new HttpServer(system.threadPoolSize, routers))
            server.ask(Bind(port)).suspend()
        case state: FutureState[BindReply] =>
            if (state.future.isFailed) state.future.causeUnsafe.printStackTrace()
            logger.info(s"http server bind port $port success")
            stack.`return`()

}

@main def server(): Unit =
    val system = ActorSystem()
    val logger = LoggerFactory.getLogger("server", system)
    logger.info("starting http server")
    system.buildActor(() => new ServerMain())
