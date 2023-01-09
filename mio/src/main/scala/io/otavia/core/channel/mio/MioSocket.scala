/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.channel.mio

import io.netty5.buffer.ComponentIterator.Next
import io.netty5.buffer.{Buffer, BufferComponent}

import java.net.ProtocolFamily

class MioSocket {

    private var boxSocket: Long = ???
    def read(buffer: Buffer): Int = {
        val iter               = buffer.forEachComponent()
        val c: BufferComponent = iter.firstWritable()
        c.writableNativeAddress()

        ???
    }

}

object MioSocket {

    def newDatagramSocket(family: ProtocolFamily): MioSocket = ???

    def newSocket(family: ProtocolFamily): MioSocket = ???

}
