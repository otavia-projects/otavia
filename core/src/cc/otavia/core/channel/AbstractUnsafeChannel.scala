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

import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.message.{ReadPlan, ReadPlanFactory}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.reactor.Reactor

import java.io.IOException
import java.net.PortUnreachableException
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

/** The [[Channel]] in [[Reactor]] */
abstract class AbstractUnsafeChannel(val channel: Channel) extends UnsafeChannel with ChannelState {

    // read sink
    protected var currentReadPlan: ReadPlan = _

    private var readFactory: ReadPlanFactory = _

    // initial channel state on constructing
    created = true

    /** true if the channel has never been registered, false otherwise */
    neverRegistered = true
    neverActive = true

    autoRead = true
    autoClose = true
    writable = true

    final private[core] def setAutoRead(auto: Boolean): Unit = this.synchronized {
        autoRead = auto
        if (!auto) clearScheduledRead()
    }

    def readPlanFactory: ReadPlanFactory = readFactory

    final private[core] def setReadPlanFactory(factory: ReadPlanFactory): Unit =
        this.synchronized { this.readFactory = factory }

    final private[core] def setAutoClose(auto: Boolean): Unit = this.synchronized { autoClose = auto }

    final protected def isAllowHalfClosure: Boolean = allowHalfClosure

    final private[core] def setAllowHalfClosure(allow: Boolean): Unit = this.synchronized { allowHalfClosure = allow }

    // write sink

    def setReadPlan(plan: ReadPlan): Unit = currentReadPlan = plan

    protected def clearScheduledRead(): Unit = {
        currentReadPlan = null
        doClearScheduledRead()
    }

    def executor: ChannelsActor[?] = channel.executor

    /** Clear any previous scheduled read. By default, this method does nothing but implementations might override it to
     *  add extra logic.
     */
    protected def doClearScheduledRead(): Unit = {
        // Do nothing by default
    }

}
