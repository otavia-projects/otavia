/*
 * Copyright 2022 Yan Kun
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

import io.otavia.core.channel.ChannelShutdownDirection.*

/** The direction of a shutdown. Depending on the direction this has different meaning. If the [[Inbound]] direction is
 *  shutdown, no more data is read from the transport and dispatched. If the [[Outbound]] direction is shutdown, no more
 *  writes will be possible to the transport and so all writes will fail.
 */
enum ChannelShutdownDirection {

    /** The inbound direction of a [[Channel]] was or should be shutdown. */
    case Inbound

    /** The outbound direction of a [[Channel]] was or should be shutdown. */
    case Outbound
}
