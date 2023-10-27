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

import cc.otavia.buffer.BufferAllocator
import cc.otavia.core.channel.ChannelOption.pool
import cc.otavia.core.channel.internal.WriteBufferWaterMark
import cc.otavia.core.channel.message.ReadPlanFactory

import java.net.NetworkInterface
import java.util.Objects.requireNonNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

class ChannelOption[T] private[otavia] (val name: String) {

    import ChannelOption.*

    if (pool.exists(name)) throw new IllegalArgumentException(s"ChannelOption with name $name already exists!")

    val id: Int = nextId.getAndIncrement()

    pool.add(name, this)

    /** Validate the value which is set for the [[ChannelOption]]. Sub-classes may override this for special checks. */
    def validate(value: T): Unit = requireNonNull(value, "value")

}

object ChannelOption {

    private val nextId: AtomicInteger = new AtomicInteger(1)

    private val pool: ChannelOptionPool = this.synchronized(new ChannelOptionPool())

    private class ChannelOptionPool() {

        private val map: ConcurrentHashMap[String, ChannelOption[?]] =
            new ConcurrentHashMap[String, ChannelOption[?]]()

        def valueOf(name: String): ChannelOption[?] =
            if (map.containsKey(name)) map.get(name) else new ChannelOption[AnyRef](name)

        def add(name: String, option: ChannelOption[?]): Unit = map.put(name, option)

        def exists(name: String): Boolean = map.containsKey(name)

    }

    /** Returns the [[ChannelOption]] of the specified name. */
    def valueOf[T](name: String): ChannelOption[T] = pool.valueOf(name).asInstanceOf[ChannelOption[T]]

    /** Shortcut of valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent). */
    def valueOf[T](firstNameComponent: Class[?], secondNameComponent: String): ChannelOption[T] =
        pool.valueOf(firstNameComponent.getName + "#" + secondNameComponent).asInstanceOf[ChannelOption[T]]

    /** Returns true if a [[ChannelOption]] exists for the given name. */
    def exists(name: String): Boolean = pool.exists(name)

    /** Creates a new [[ChannelOption]] for the given name or fail with an [[IllegalArgumentException]] if a
     *  [[ChannelOption]] for the given name exists.
     *
     *  @deprecated
     *    use [[valueOf]]
     */
    private def newInstance[T](name: String): ChannelOption[T] = new ChannelOption[T](name)

    val READ_PLAN_FACTORY: ChannelOption[ReadPlanFactory] = newInstance("READ_PLAN_FACTORY")

    val CHANNEL_FUTURE_BARRIER: ChannelOption[AnyRef => Boolean] = newInstance("CHANNEL_FUTURE_BARRIER")
    val CHANNEL_STACK_BARRIER: ChannelOption[AnyRef => Boolean]  = newInstance("CHANNEL_STACK_BARRIER")
    val CHANNEL_MAX_STACK_INFLIGHT: ChannelOption[Int]           = newInstance("CHANNEL_MAX_STACK_INFLIGHT")
    val CHANNEL_MAX_FUTURE_INFLIGHT: ChannelOption[Int]          = newInstance("CHANNEL_MAX_FUTURE_INFLIGHT")
    val CHANNEL_STACK_HEAD_OF_LINE: ChannelOption[Boolean]       = newInstance("CHANNEL_STACK_HEAD_OF_LINE")

    val CONNECT_TIMEOUT_MILLIS: ChannelOption[Integer] = newInstance("CONNECT_TIMEOUT_MILLIS")

    val WRITE_BUFFER_WATER_MARK: ChannelOption[WriteBufferWaterMark] = newInstance("WRITE_BUFFER_WATER_MARK")

    val ALLOW_HALF_CLOSURE: ChannelOption[Boolean] = newInstance("ALLOW_HALF_CLOSURE")
    val AUTO_READ: ChannelOption[Boolean]          = newInstance("AUTO_READ")

    /** If true then the [[Channel]] is closed automatically and immediately on write failure. The default value is
     *  true.
     */
    val AUTO_CLOSE: ChannelOption[Boolean] = newInstance("AUTO_CLOSE")

    val SO_BROADCAST: ChannelOption[Boolean] = newInstance("SO_BROADCAST")
    val SO_KEEPALIVE: ChannelOption[Boolean] = newInstance("SO_KEEPALIVE")
    val SO_SNDBUF: ChannelOption[Integer]    = newInstance("SO_SNDBUF")
    val SO_RCVBUF: ChannelOption[Integer]    = newInstance("SO_RCVBUF")
    val SO_REUSEADDR: ChannelOption[Boolean] = newInstance("SO_REUSEADDR")
    val SO_LINGER: ChannelOption[Integer]    = newInstance("SO_LINGER")
    val SO_BACKLOG: ChannelOption[Integer]   = newInstance("SO_BACKLOG")
    val SO_TIMEOUT: ChannelOption[Integer]   = newInstance("SO_TIMEOUT")

    val IP_TOS: ChannelOption[Integer]                     = newInstance("IP_TOS")
    val IP_MULTICAST_IF: ChannelOption[NetworkInterface]   = newInstance("IP_MULTICAST_IF")
    val IP_MULTICAST_TTL: ChannelOption[Integer]           = newInstance("IP_MULTICAST_TTL")
    val IP_MULTICAST_LOOP_DISABLED: ChannelOption[Boolean] = newInstance("IP_MULTICAST_LOOP_DISABLED")

    val TCP_NODELAY: ChannelOption[Boolean] = newInstance("TCP_NODELAY")

    /** Client-side TCP FastOpen. Sending data with the initial TCP handshake. */
    val TCP_FASTOPEN_CONNECT: ChannelOption[Boolean] = newInstance("TCP_FASTOPEN_CONNECT")

    /** Server-side TCP FastOpen. Configures the maximum number of outstanding (waiting to be accepted) TFO connections.
     */
    val TCP_FASTOPEN: ChannelOption[Integer] = valueOf(classOf[ChannelOption[?]], "TCP_FASTOPEN")

    val DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION: ChannelOption[Boolean] =
        newInstance("DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION")

}
