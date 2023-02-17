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

package io.otavia.core.channel.udp

import io.netty5.buffer.Buffer
import io.netty5.util.{Resource, Send}

import java.net.SocketAddress

/** Base class for addressed envelopes that have [[Buffer]] instances as messages.
 *  @param message
 *  @param recipient
 *  @param sender
 *  @tparam A
 *    The type of socket address used for recipient and sender.
 *  @tparam T
 *    The concrete sub-type of this class, used for implementing [[send]].
 */
abstract class BufferAddressedEnvelope[A <: SocketAddress, T <: BufferAddressedEnvelope[A, T]](
    message: Buffer,
    recipient: A,
    sender: Option[A] = None
) extends DefaultAddressedEnvelope[Buffer, A](message, recipient, sender),
      Resource[T] {

    override def send(): Send[T] = {
        val contentSend = content.send().nn
        Send.sending(getClass.asInstanceOf[Class[T]], () => replace(contentSend.receive().nn)).nn
    }

    /** Create a new addressed envelope instance, that has the same recipient and sender as this one, but the given
     *  content.
     *
     *  @param content
     *    The contents of the returned addressed envelope instance.
     *  @return
     *    An addressed envelope instance that has the same recipient and sender as this one, but the given content.
     */
    def replace(content: Buffer): T

    override def close(): Unit = content.close()

    override def isAccessible: Boolean = content.isAccessible

}
