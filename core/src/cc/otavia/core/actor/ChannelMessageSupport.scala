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

package cc.otavia.core.actor

import cc.otavia.core.message.{ChannelTimeoutEvent, ReactorEvent}
import cc.otavia.core.stack.ChannelStack

/** Kernel-internal capability trait for actors that manage IO [[cc.otavia.core.channel.Channel]] instances.
 *
 *  Only [[ChannelsActor]] implements this trait. It separates channel-related dispatch (reactor events, channel
 *  timeouts, channel stacks) from the core [[AbstractActor]] stack coroutine engine, ensuring that [[StateActor]]'s
 *  inheritance chain remains free of IO concerns.
 *
 *  All methods are [[private[core]]] — they are invisible to user code and only called by the kernel dispatch machinery.
 */
private[core] trait ChannelMessageSupport {

    /** Dispatch a channel stack for processing by the actor's [[resumeChannelStack]] logic. Called by the kernel when
     *  a channel's inflight mechanism produces a [[ChannelStack]] for actor processing, or when a suspended channel
     *  stack is resumed after a promise completion.
     *
     *  @param stack
     *    the channel stack to dispatch
     */
    private[core] def dispatchChannelStack(stack: ChannelStack[?]): Unit

    /** Handle a reactor IO event (register, deregister, read, close, etc.). Only relevant for [[ChannelsActor]]
     *  instances that manage channel lifecycles.
     *
     *  @param event
     *    the reactor event
     */
    private[core] def receiveReactorEvent(event: ReactorEvent): Unit

    /** Handle a channel timeout event from the timer system. Only relevant for [[ChannelsActor]] instances.
     *
     *  @param event
     *    the timeout event
     */
    private[core] def receiveChannelTimeoutEvent(event: ChannelTimeoutEvent): Unit

    /** Receive an inbound channel message (decoded by the pipeline's tail handler). Creates and dispatches a
     *  [[ChannelStack]] for actor processing.
     *
     *  @param stack
     *    the channel stack containing the inbound message
     */
    private[core] def receiveChannelMessage(stack: ChannelStack[?]): Unit

}
