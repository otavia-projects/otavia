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

package cc.otavia.core.channel.internal

import cc.otavia.core.cache.ActorThreadLocal
import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.channel.{ChannelHandler, ChannelHandlerContext, ChannelShutdownDirection, Skip}
import cc.otavia.core.stack.ChannelFuture

import java.lang.annotation.*
import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.annotation.StaticAnnotation
import scala.collection.mutable
import scala.language.unsafeNulls

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

    private val MASKS = new ActorThreadLocal[mutable.HashMap[Class[? <: ChannelHandler], Int]] {
        override protected def initialValue(): mutable.HashMap[Class[_ <: ChannelHandler], Int] = mutable.HashMap.empty
    }

    def mask(clazz: Class[? <: ChannelHandler]): Int = {
        // Try to obtain the mask from the cache first. If this fails calculate it and put it in the cache for fast
        // lookup in the future.
        val map       = MASKS.get()
        var mask: Int = 0
        if (!map.contains(clazz)) {
            mask = mask0(clazz)
            map.put(clazz, mask)
        } else mask = map(clazz)

        mask
    }

    private def mask0(handlerType: Class[? <: ChannelHandler]): Int = {
        var mask = MASK_ALL_INBOUND | MASK_ALL_OUTBOUND

        if (isSkip(handlerType, "channelExceptionCaught", classOf[ChannelHandlerContext], classOf[Throwable]))
            mask &= ~MASK_CHANNEL_EXCEPTION_CAUGHT
        if (isSkip(handlerType, "channelRegistered", classOf[ChannelHandlerContext])) mask &= ~MASK_CHANNEL_REGISTERED
        if (isSkip(handlerType, "channelUnregistered", classOf[ChannelHandlerContext]))
            mask &= ~MASK_CHANNEL_UNREGISTERED
        if (isSkip(handlerType, "channelActive", classOf[ChannelHandlerContext])) mask &= ~MASK_CHANNEL_ACTIVE
        if (isSkip(handlerType, "channelInactive", classOf[ChannelHandlerContext])) mask &= ~MASK_CHANNEL_INACTIVE
        if (isSkip(handlerType, "channelShutdown", classOf[ChannelHandlerContext], classOf[ChannelShutdownDirection]))
            mask &= ~MASK_CHANNEL_SHUTDOWN
        if (isSkip(handlerType, "channelRead", classOf[ChannelHandlerContext], classOf[AnyRef]))
            mask &= ~MASK_CHANNEL_READ
        if (isSkip(handlerType, "channelRead", classOf[ChannelHandlerContext], classOf[AnyRef], classOf[Long]))
            mask &= ~MASK_CHANNEL_READ_ID
        if (isSkip(handlerType, "channelReadComplete", classOf[ChannelHandlerContext]))
            mask &= ~MASK_CHANNEL_READ_COMPLETE
        if (isSkip(handlerType, "channelWritabilityChanged", classOf[ChannelHandlerContext]))
            mask &= ~MASK_CHANNEL_WRITABILITY_CHANGED
        if (isSkip(handlerType, "channelInboundEvent", classOf[ChannelHandlerContext], classOf[AnyRef]))
            mask &= ~MASK_CHANNEL_INBOUND_EVENT
        if (isSkip(handlerType, "channelTimeoutEvent", classOf[ChannelHandlerContext], classOf[Long]))
            mask &= ~MASK_CHANNEL_TIMEOUT_EVENT

        if (isSkip(handlerType, "bind", classOf[ChannelHandlerContext], classOf[SocketAddress], classOf[ChannelFuture]))
            mask &= ~MASK_BIND
        if (
          isSkip(
            handlerType,
            "connect",
            classOf[ChannelHandlerContext],
            classOf[SocketAddress],
            classOf[Option[SocketAddress]],
            classOf[ChannelFuture]
          )
        ) mask &= ~MASK_CONNECT
        if (isSkip(handlerType, "disconnect", classOf[ChannelHandlerContext], classOf[ChannelFuture]))
            mask &= ~MASK_DISCONNECT
        if (isSkip(handlerType, "close", classOf[ChannelHandlerContext], classOf[ChannelFuture])) mask &= ~MASK_CLOSE
        if (
          isSkip(
            handlerType,
            "shutdown",
            classOf[ChannelHandlerContext],
            classOf[ChannelShutdownDirection],
            classOf[ChannelFuture]
          )
        ) mask &= ~MASK_SHUTDOWN
        if (isSkip(handlerType, "register", classOf[ChannelHandlerContext], classOf[ChannelFuture]))
            mask &= ~MASK_REGISTER
        if (isSkip(handlerType, "deregister", classOf[ChannelHandlerContext], classOf[ChannelFuture]))
            mask &= ~MASK_DEREGISTER
        if (isSkip(handlerType, "read", classOf[ChannelHandlerContext], classOf[ReadPlan])) mask &= ~MASK_READ
        if (isSkip(handlerType, "write", classOf[ChannelHandlerContext], classOf[AnyRef])) mask &= ~MASK_WRITE
        if (isSkip(handlerType, "write", classOf[ChannelHandlerContext], classOf[AnyRef], classOf[Long]))
            mask &= ~MASK_WRITE_ID
        if (isSkip(handlerType, "flush", classOf[ChannelHandlerContext])) mask &= ~MASK_FLUSH
        if (isSkip(handlerType, "sendOutboundEvent", classOf[ChannelHandlerContext], classOf[AnyRef]))
            mask &= ~MASK_SEND_OUTBOUND_EVENT
        if (isSkip(handlerType, "pendingOutboundBytes", classOf[ChannelHandlerContext]))
            mask &= ~MASK_PENDING_OUTBOUND_BYTES

        if (
          isSkip(
            handlerType,
            "open",
            classOf[ChannelHandlerContext],
            classOf[Path],
            classOf[Seq[OpenOption]],
            classOf[Seq[FileAttribute[?]]],
            classOf[ChannelFuture]
          )
        ) mask &= ~MASK_OPEN

        mask
    }

    private[core] def isInbound(clazz: Class[? <: ChannelHandler]) = (mask(clazz) & MASK_ALL_INBOUND) != 0

    private[core] def isOutbound(clazz: Class[? <: ChannelHandler]) = (mask(clazz) & MASK_ALL_OUTBOUND) != 0

    private def isSkip(handlerType: Class[?], methodName: String, paramTypes: Class[?]*): Boolean = {
        var skip = false
        try {
            skip = handlerType.getMethod(methodName, paramTypes*).isAnnotationPresent(classOf[Skip])
        } catch {
            case _: Throwable =>
        }
        skip
    }

}
