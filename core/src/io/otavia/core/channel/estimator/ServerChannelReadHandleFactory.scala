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

package io.otavia.core.channel.estimator

import io.otavia.core.channel.estimator.MaxMessagesReadHandleFactory

/** [[MaxMessagesReadHandleFactory]] implementation which should be used for [[NioServerSocketChannel]]s. */
class ServerChannelReadHandleFactory(maxMessagesPerRead: Int = 16)
    extends MaxMessagesReadHandleFactory(maxMessagesPerRead) {
    override protected def newMaxMessageHandle(
        maxMessagesPerRead: Int
    ): MaxMessagesReadHandleFactory.MaxMessageReadHandle =
        new MaxMessagesReadHandleFactory.MaxMessageReadHandle(maxMessagesPerRead) {
            override def estimatedBufferCapacity: Int = 128
        }

}