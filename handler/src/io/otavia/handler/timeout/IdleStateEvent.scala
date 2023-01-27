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

package io.otavia.handler.timeout

import io.otavia.core.channel.Channel

/** A user event triggered by [[IdleStateHandler]] when a [[Channel]] is idle.
 *
 *  @param state
 *    the [[IdleStateEvent]] which triggered the event.
 *  @param first
 *    `true` if its the first idle event for the [[IdleStateEvent]].
 */
enum IdleStateEvent(val state: IdleState, val first: Boolean) {

    case FIRST_READER_IDLE_STATE_EVENT extends IdleStateEvent(IdleState.READER_IDLE, true)
    case READER_IDLE_STATE_EVENT       extends IdleStateEvent(IdleState.READER_IDLE, false)
    case FIRST_WRITER_IDLE_STATE_EVENT extends IdleStateEvent(IdleState.WRITER_IDLE, true)
    case WRITER_IDLE_STATE_EVENT       extends IdleStateEvent(IdleState.WRITER_IDLE, false)
    case FIRST_ALL_IDLE_STATE_EVENT    extends IdleStateEvent(IdleState.ALL_IDLE, true)
    case ALL_IDLE_STATE_EVENT          extends IdleStateEvent(IdleState.ALL_IDLE, false)

    override def toString: String =
        s"IdleStateEvent($state${if (first) ", first" else ""})"

}
