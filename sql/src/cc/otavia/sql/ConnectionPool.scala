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

package cc.otavia.sql

import cc.otavia.core.actor.SocketChannelsActor.Connect
import cc.otavia.core.actor.{ChannelsActor, SocketChannelsActor}
import cc.otavia.core.channel.{Channel, ChannelAddress, ChannelInitializer, ChannelState}
import cc.otavia.core.message.{Ask, ExceptionMessage, Reply}
import cc.otavia.core.stack.StackState.*
import cc.otavia.core.stack.helper.*
import cc.otavia.core.stack.{AskStack, ChannelFuture, StackState}
import cc.otavia.sql.Authentication
import cc.otavia.sql.Statement.*

import java.net.{ProtocolFamily, StandardProtocolFamily}

class ConnectionPool(val size: Int, override val family: ProtocolFamily = StandardProtocolFamily.INET)
    extends SocketChannelsActor[Connection.MSG] {

    private var channels: Array[ChannelAddress] = _
    private var drivers: Array[Driver]          = _

    // TODO

}
