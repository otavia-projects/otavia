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

/** A enum that represents the idle state of a [[io.otavia.core.channel.Channel]] */
enum IdleState {

    /** No data was received for a while. */
    case READER_IDLE

    /** No data was sent for a while. */
    case WRITER_IDLE

    /** No data was either received or sent for a while. */
    case ALL_IDLE

}
