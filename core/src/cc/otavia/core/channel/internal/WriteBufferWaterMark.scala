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

package cc.otavia.core.channel.internal

import io.netty5.util.internal.ObjectUtil.checkPositiveOrZero

/** [[WriteBufferWaterMark]] is used to set low water mark and high water mark for the write buffer.
 *
 *  If the number of bytes queued in the write buffer exceeds the high high water mark, Channel#writableBytes() will
 *  start to return 0.
 *
 *  If the number of bytes queued in the write buffer exceeds the high high water mark and then dropped down below the
 *  low low water mark, Channel#writableBytes() will start to return a positive value again.
 *
 *  @param low
 *    the low water mark for the write buffer.
 *  @param high
 *    the high water mark for the write buffer.
 *  @param validate
 */
final class WriteBufferWaterMark private[core] (val low: Int, val high: Int, validate: Boolean = true) {

    if (validate) {
        checkPositiveOrZero(low, "low")
        if (high < low)
            throw new IllegalArgumentException(
              s"write buffer's high water mark cannot be less than low water mark ($low): $high"
            )
    }

    override def toString: String = {
        val builder =
            new StringBuilder(55)
                .append("WriteBufferWaterMark(low: ")
                .append(low)
                .append(", high: ")
                .append(high)
                .append(")")
        builder.toString
    }

}

object WriteBufferWaterMark {

    val DEFAULT_LOW_WATER_MARK: Int  = 32 * 1024
    val DEFAULT_HIGH_WATER_MARK: Int = 64 * 1024
    val DEFAULT                      = new WriteBufferWaterMark(DEFAULT_LOW_WATER_MARK, DEFAULT_HIGH_WATER_MARK, false)

}
