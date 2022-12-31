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

/** Responsible to estimate the size of a message. The size represents approximately how much memory the message will
 *  reserve in memory.
 */
object MessageSizeEstimator {
    trait Handle {

        /** Calculate the size of the given message.
         *
         *  @param msg
         *    The message for which the size should be calculated
         *  @return
         *    size The size in bytes. The returned size must be >= 0
         */
        def size(msg: AnyRef): Int
    }
}

trait MessageSizeEstimator {

    /** Creates a new handle. The handle provides the actual operations. */
    def newHandle: MessageSizeEstimator.Handle
}
