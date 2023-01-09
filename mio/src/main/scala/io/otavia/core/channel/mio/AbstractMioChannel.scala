/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.channel.mio

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.*

import java.net.SocketAddress
import java.nio.channels.SelectableChannel

abstract class AbstractMioChannel[L <: SocketAddress, R <: SocketAddress](
    executor: ChannelsActor[?],
    supportingDisconnect: Boolean,
    initialFlag: Int,
    defaultReadHandleFactory: ReadHandleFactory,
    defaultWriteHandleFactory: WriteHandleFactory,
    val socket: MioSocket,
    val remote: SocketAddress
) extends AbstractChannel[L, R](executor, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory) {

    override protected[channel] def doReadNow(readSink: ReadSink): Boolean = ???

    override protected def doWriteNow(writeSink: WriteSink): Unit = ???

}
