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

import io.otavia.buffer.BufferAllocator
import io.netty5.util.{AbstractConstant, ConstantPool}
import io.otavia.core.channel.ChannelOption.pool
import io.otavia.core.channel.estimator.{MessageSizeEstimator, ReadHandleFactory, WriteHandleFactory}
import io.otavia.core.channel.internal.WriteBufferWaterMark

import java.net.NetworkInterface
import java.util.Objects.requireNonNull

class ChannelOption[T](id: Int, name: String) extends AbstractConstant[ChannelOption[T]](id, name) {

    def this(name: String) = this(pool.nextId(), name)

    /** Validate the value which is set for the [[ChannelOption]]. Sub-classes may override this for special checks. */
    def validate(value: T): Unit = requireNonNull(value, "value")

}

object ChannelOption {

    private val pool: ConstantPool[ChannelOption[AnyRef]] = new ConstantPool[ChannelOption[AnyRef]]() {
        override protected def newConstant(id: Int, name: String): ChannelOption[AnyRef] = {
            new ChannelOption[AnyRef](id, name)
        }
    }

    /** Returns the [[ChannelOption]] of the specified name. */
    @SuppressWarnings(Array("unchecked"))
    def valueOf[T](name: String): ChannelOption[T] = pool.valueOf(name).asInstanceOf[ChannelOption[T]]

    /** Shortcut of valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent). */
    @SuppressWarnings(Array("unchecked"))
    def valueOf[T](firstNameComponent: Class[?], secondNameComponent: String): ChannelOption[T] =
        pool.valueOf(firstNameComponent, secondNameComponent).asInstanceOf[ChannelOption[T]]

    /** Returns true if a [[ChannelOption]] exists for the given name. */
    def exists(name: String): Boolean = pool.exists(name)

    /** Creates a new [[ChannelOption]] for the given name or fail with an [[IllegalArgumentException]] if a
     *  [[ChannelOption]] for the given name exists.
     *
     *  @deprecated
     *    use [[valueOf]]
     */
    @deprecated
    @SuppressWarnings(Array("unchecked"))
    def newInstance[T](name: String): ChannelOption[T] = pool.newInstance(name).asInstanceOf[ChannelOption[T]]

    val BUFFER_ALLOCATOR: ChannelOption[BufferAllocator]            = valueOf("BUFFER_ALLOCATOR")
    val READ_HANDLE_FACTORY: ChannelOption[ReadHandleFactory]       = valueOf("READ_HANDLE_FACTORY")
    val WRITE_HANDLE_FACTORY: ChannelOption[WriteHandleFactory]     = valueOf("WRITE_HANDLE_FACTORY")
    val MESSAGE_SIZE_ESTIMATOR: ChannelOption[MessageSizeEstimator] = valueOf("MESSAGE_SIZE_ESTIMATOR")

    val CONNECT_TIMEOUT_MILLIS: ChannelOption[Integer] = valueOf("CONNECT_TIMEOUT_MILLIS")

    val MAX_MESSAGES_PER_WRITE: ChannelOption[Integer] = valueOf("MAX_MESSAGES_PER_WRITE")

    val WRITE_BUFFER_WATER_MARK: ChannelOption[WriteBufferWaterMark] = valueOf("WRITE_BUFFER_WATER_MARK")

    val ALLOW_HALF_CLOSURE: ChannelOption[Boolean] = valueOf("ALLOW_HALF_CLOSURE")
    val AUTO_READ: ChannelOption[Boolean]          = valueOf("AUTO_READ")

    /** If true then the [[Channel]] is closed automatically and immediately on write failure. The default value is
     *  true.
     */
    val AUTO_CLOSE: ChannelOption[Boolean] = valueOf("AUTO_CLOSE")

    val SO_BROADCAST: ChannelOption[Boolean] = valueOf("SO_BROADCAST")
    val SO_KEEPALIVE: ChannelOption[Boolean] = valueOf("SO_KEEPALIVE")
    val SO_SNDBUF: ChannelOption[Integer]    = valueOf("SO_SNDBUF")
    val SO_RCVBUF: ChannelOption[Integer]    = valueOf("SO_RCVBUF")
    val SO_REUSEADDR: ChannelOption[Boolean] = valueOf("SO_REUSEADDR")
    val SO_LINGER: ChannelOption[Integer]    = valueOf("SO_LINGER")
    val SO_BACKLOG: ChannelOption[Integer]   = valueOf("SO_BACKLOG")
    val SO_TIMEOUT: ChannelOption[Integer]   = valueOf("SO_TIMEOUT")

    val IP_TOS: ChannelOption[Integer]                     = valueOf("IP_TOS")
    val IP_MULTICAST_IF: ChannelOption[NetworkInterface]   = valueOf("IP_MULTICAST_IF")
    val IP_MULTICAST_TTL: ChannelOption[Integer]           = valueOf("IP_MULTICAST_TTL")
    val IP_MULTICAST_LOOP_DISABLED: ChannelOption[Boolean] = valueOf("IP_MULTICAST_LOOP_DISABLED")

    val TCP_NODELAY: ChannelOption[Boolean] = valueOf("TCP_NODELAY")

    /** Client-side TCP FastOpen. Sending data with the initial TCP handshake. */
    val TCP_FASTOPEN_CONNECT: ChannelOption[Boolean] = valueOf("TCP_FASTOPEN_CONNECT")

    /** Server-side TCP FastOpen. Configures the maximum number of outstanding (waiting to be accepted) TFO connections.
     */
    val TCP_FASTOPEN: ChannelOption[Integer] = valueOf(classOf[ChannelOption[?]], "TCP_FASTOPEN")

    val DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION: ChannelOption[Boolean] = valueOf(
      "DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION"
    )

}
