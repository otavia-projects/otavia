/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.core.channel

import cc.otavia.core.message.*
import cc.otavia.core.stack.ChannelPromise
import cc.otavia.core.system.ActorSystem

import scala.language.unsafeNulls

abstract class AbstractDatagramChannel(system: ActorSystem) extends AbstractNetworkChannel(system) {

    override private[core] def disconnectTransport(promise: ChannelPromise): Unit = {
        if (!connected) promise.setFailure(new IllegalStateException())
        else {
            disconnecting = true
            this.ongoingChannelPromise = promise
            mountedThread.ioHandler.disconnect(this)
            // reactor.disconnect(this)
        }
    }

    override private[core] def handleChannelDisconnectReply(cause: Option[Throwable]): Unit = {
        disconnecting = false
        val promise = this.ongoingChannelPromise
        this.ongoingChannelPromise = null
        cause match
            case None =>
                disconnected = true
                promise.setSuccess(EMPTY_EVENT)
            case Some(cause) =>
                promise.setFailure(cause)
                closeTransport(newPromise())
    }

}
