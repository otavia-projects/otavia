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

import cc.otavia.core.address.ActorAddress
import cc.otavia.core.buffer.{AbstractPageAllocator, PageBufferAllocator}
import cc.otavia.core.channel.message.ReadPlan

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

trait UnsafeChannel {

    def channel: Channel

    final def executorAddress: ActorAddress[?] = channel.executorAddress

    /** Bind the [[Channel]] to the [[SocketAddress]]
     *
     *  @param localAddress
     *    the [[SocketAddress]] to bound to.
     *  @throws Exception
     *    when an error happens.
     */
    @throws[Exception]
    def unsafeBind(local: SocketAddress): Unit

    @throws[Exception]
    def unsafeOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit

    @throws[Exception]
    def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit

    /** Disconnect this [[Channel]] from its remote peer
     *
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    def unsafeDisconnect(): Unit

    /** Close the [[Channel]]
     *
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    def unsafeClose(): Unit

    /** Shutdown one direction of the [[Channel]].
     *
     *  @param direction
     *    the direction to shut unsafewn.
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    def unsafeShutdown(direction: ChannelShutdownDirection): Unit

    def setReadPlan(plan: ReadPlan): Unit

    /** Schedule a read operation.
     *
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    def unsafeRead(readPlan: ReadPlan): Unit

    /** Returns true if the [[UnsafeChannel]] is open and may get active later */
    def isOpen: Boolean

    /** Return true if the [[UnsafeChannel]] is active and so connected. */
    def isActive: Boolean

    /** Returns true if the [[ChannelShutdownDirection]] of the [[UnsafeChannel]] was shutdown before.
     *  @param direction
     *    shutdown direction
     *  @return
     */
    def isShutdown(direction: ChannelShutdownDirection): Boolean

    def directAllocator: PageBufferAllocator = channel.directAllocator

    def heapAllocator: PageBufferAllocator = channel.heapAllocator

}
