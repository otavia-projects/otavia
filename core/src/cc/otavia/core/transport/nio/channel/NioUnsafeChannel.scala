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

package cc.otavia.core.transport.nio.channel

import cc.otavia.core.channel.UnsafeChannel

import java.nio.channels.{ClosedChannelException, SelectionKey, Selector}

/** Process IO. */
trait NioUnsafeChannel extends UnsafeChannel {

    /** Register to a [[Selector]].
     *
     *  @param selector
     *    the [[Selector]] to register to.
     *  @throws ClosedChannelException
     *    if already closed.
     */
    @throws[ClosedChannelException]
    def registerSelector(selector: Selector): Unit

    /** Deregister from previous registered [[Selector]]. */
    def deregisterSelector(): Unit

    /** Handle some IO for the given [[SelectionKey]].
     *
     *  @param key
     *    the [[SelectionKey]] that needs to be handled.
     */
    def handle(key: SelectionKey): Unit

    /** Close this processor. */
    def closeProcessor(): Unit

}
