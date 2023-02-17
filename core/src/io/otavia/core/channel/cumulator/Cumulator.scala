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

package io.otavia.core.channel.cumulator

import io.netty5.buffer.{Buffer, BufferAllocator}

trait Cumulator {

    /** Cumulate the given [[Buffer]]s and return the [[Buffer]] that holds the cumulated bytes. The implementation is
     *  responsible to correctly handle the life-cycle of the given [[Buffer]]s and so call [[Buffer.close]] if a
     *  [[Buffer]] is fully consumed.
     */
    def cumulate(alloc: BufferAllocator, accumulation: Buffer, in: Buffer): Buffer

    /** Consume the given buffer and return a new buffer with the same readable data, but where any data before the read
     *  offset may have been removed. The returned buffer may be the same buffer instance as the buffer passed in.
     *
     *  @param accumulation
     *    The buffer we wish to trim already processed bytes from.
     *  @return
     *    A buffer where the bytes before the reader-offset have been removed.
     */
    def discardSomeReadBytes(accumulation: Buffer): Buffer

}
