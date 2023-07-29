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

package io.otavia.core.channel

import io.otavia.core.message.ReactorEvent
import io.otavia.core.stack.ChannelPromise
import io.otavia.core.system.ActorSystem

import scala.language.unsafeNulls

abstract class AbstractDatagramChannel(system: ActorSystem) extends AbstractNetworkChannel(system) {

    override private[core] def disconnectTransport(promise: ChannelPromise): Unit = {
        if (!connected) promise.setFailure(new IllegalStateException())
        else {
            disconnecting = true
            this.ongoingChannelPromise = promise
            reactor.disconnect(this)
        }
    }

    override private[core] def handleChannelDisconnectReplyEvent(event: ReactorEvent.DisconnectReply): Unit = {
        disconnecting = false
        val promise = this.ongoingChannelPromise
        this.ongoingChannelPromise = null
        event.cause match
            case None =>
                disconnected = true
                promise.setSuccess(event)
            case Some(cause) =>
                promise.setFailure(cause)
                closeTransport(newPromise())
    }

}
