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

package io.otavia.handler.codec

import io.otavia.core.channel.Channel

/** A [[CodecException]] which is thrown when a [[Channel]] is closed unexpectedly before the codec finishes handling
 *  the current message, such as missing response while waiting for a request.
 */
class PrematureChannelClosureException(message: String | Null, cause: Throwable | Null)
    extends CodecException(message, cause) {

    def this() = this(null, null)

    def this(message: String) = this(message, null)

    def this(cause: Throwable) = this(null, cause)

}
