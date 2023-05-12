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

package io.otavia.core.channel.internal

import io.netty5.util.concurrent.FastThreadLocal
import io.otavia.core.channel.ChannelHandler

import scala.collection.mutable

object ChannelHandlerMask {

    // Using to mask which methods must be called for a ChannelHandler.
    private[core] val MASK_CHANNEL_EXCEPTION_CAUGHT    = 1
    private[core] val MASK_CHANNEL_REGISTERED          = 1 << 1
    private[core] val MASK_CHANNEL_UNREGISTERED        = 1 << 2
    private[core] val MASK_CHANNEL_ACTIVE              = 1 << 3
    private[core] val MASK_CHANNEL_INACTIVE            = 1 << 4
    private[core] val MASK_CHANNEL_SHUTDOWN            = 1 << 5
    private[core] val MASK_CHANNEL_READ                = 1 << 6
    private[core] val MASK_CHANNEL_READ_COMPLETE       = 1 << 7
    private[core] val MASK_CHANNEL_INBOUND_EVENT       = 1 << 8
    private[core] val MASK_CHANNEL_WRITABILITY_CHANGED = 1 << 9
    private[core] val MASK_BIND                        = 1 << 10
    private[core] val MASK_CONNECT                     = 1 << 11
    private[core] val MASK_DISCONNECT                  = 1 << 12
    private[core] val MASK_CLOSE                       = 1 << 13
    private[core] val MASK_SHUTDOWN                    = 1 << 14
    private[core] val MASK_REGISTER                    = 1 << 15
    private[core] val MASK_DEREGISTER                  = 1 << 16
    private[core] val MASK_READ                        = 1 << 17
    private[core] val MASK_WRITE                       = 1 << 18
    private[core] val MASK_FLUSH                       = 1 << 19
    private[core] val MASK_SEND_OUTBOUND_EVENT         = 1 << 20

    private[core] val MASK_PENDING_OUTBOUND_BYTES = 1 << 21

    private[core] val MASK_CHANNEL_TIMEOUT_EVENT = 1 << 22

    private[core] val MASK_CHANNEL_READ_ID = 1 << 23
    private[core] val MASK_WRITE_ID        = 1 << 24
    private[core] val MASK_OPEN            = 1 << 25

    private val MASK_ALL_INBOUND =
        MASK_CHANNEL_EXCEPTION_CAUGHT | MASK_CHANNEL_REGISTERED | MASK_CHANNEL_UNREGISTERED | MASK_CHANNEL_ACTIVE |
            MASK_CHANNEL_INACTIVE | MASK_CHANNEL_SHUTDOWN | MASK_CHANNEL_READ | MASK_CHANNEL_READ_COMPLETE |
            MASK_CHANNEL_WRITABILITY_CHANGED | MASK_CHANNEL_INBOUND_EVENT | MASK_CHANNEL_TIMEOUT_EVENT |
            MASK_CHANNEL_READ_ID
    private val MASK_ALL_OUTBOUND =
        MASK_BIND | MASK_CONNECT | MASK_DISCONNECT | MASK_CLOSE | MASK_SHUTDOWN | MASK_REGISTER | MASK_DEREGISTER |
            MASK_READ | MASK_WRITE | MASK_FLUSH | MASK_SEND_OUTBOUND_EVENT | MASK_PENDING_OUTBOUND_BYTES |
            MASK_WRITE_ID | MASK_OPEN

    private val MASKS = new FastThreadLocal[mutable.HashMap[Class[? <: ChannelHandler], Int]] {
        override def initialValue(): mutable.HashMap[Class[? <: ChannelHandler], Int] = mutable.HashMap.empty
    }

    def mask(clazz: Class[? <: ChannelHandler]): Int = {
        ???
    }
    private def mask0(handlerType: Class[? <: ChannelHandler]): Int = ???

    private[core] def isInbound(clazz: Class[? <: ChannelHandler]) = (mask(clazz) & MASK_ALL_INBOUND) != 0

    private[core] def isOutbound(clazz: Class[? <: ChannelHandler]) = (mask(clazz) & MASK_ALL_OUTBOUND) != 0

    private def isSkippable(handlerType: Class[?], methodName: String, paramTypes: Class[?]*): Boolean = {
        ???
    }

}
