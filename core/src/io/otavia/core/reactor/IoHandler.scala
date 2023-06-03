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

package io.otavia.core.reactor

import io.otavia.core.channel.Channel
import io.otavia.core.system.ActorSystem

/** Handles IO dispatching for an [[io.otavia.core.actor.ChannelsActor]] All operations except [[wakeup]] and
 *  [[isCompatible]] <strong>MUST</strong> be executed on the [[io.otavia.core.reactor.Reactor]] and should never be
 *  called from the user-directly.
 */
abstract class IoHandler(val system: ActorSystem) {

    /** Run the IO handled by this [[IoHandler]]. The [[IoExecutionContext]] should be used to ensure we not execute too
     *  long and so block the processing of other task that are scheduled on the [[io.otavia.core.actor.ChannelsActor]]
     *  . This is done by taking [[IoExecutionContext.delayNanos]] or [[IoExecutionContext.deadlineNanos]] into account.
     *
     *  @return
     *    the number of [[IoHandle]] for which I/O was handled.
     */
    def run(context: IoExecutionContext): Int

    /** Prepare to destroy this [[IoHandler]]. This method will be called before [[destroy]] and may be called multiple
     *  times.
     */
    def prepareToDestroy(): Unit

    /** Destroy the [[IoHandler]] and free all its resources. */
    def destroy(): Unit

    /** Register a [[Channel]] for IO.
     *  @param channel
     *    the [[Channel]] to register.
     */
    @throws[Exception]
    def register(channel: Channel): Unit

    /** Deregister a [[Channel]] for IO.
     *
     *  @param channel
     *    the [[Channel]] to deregister..
     *  @throws Exception
     *    thrown if an error happens during deregistration.
     */
    @throws[Exception]
    def deregister(channel: Channel): Unit

    /** Wakeup the [[IoHandler]], which means if any operation blocks it should be unblocked and return as soon as
     *  possible.
     */
    def wakeup(inEventLoop: Boolean): Unit

    /** Returns true if the given type is compatible with this [[IoHandler]] and so can be registered, false otherwise.
     *
     *  @param handleType
     *    the type of the [[Channel]].
     *  @return
     *    if compatible of not.
     */
    def isCompatible(handleType: Class[? <: Channel]): Boolean

}
