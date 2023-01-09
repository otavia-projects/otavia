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

package io.otavia.core.reactor

import java.util.function.IntSupplier

/** Select strategy interface.
 *
 *  Provides the ability to control the behavior of the select loop. For example a blocking select operation can be
 *  delayed or skipped entirely if there are events to process immediately.
 */
object SelectStrategy {

    /** Indicates a blocking select should follow. */
    val SELECT: Int = -1

    /** Indicates the IO loop should be retried, no blocking select to follow directly. */
    val CONTINUE: Int = -2

    /** Indicates the IO loop to poll for new events without blocking. */
    val BUSY_WAIT: Int = -3
}

trait SelectStrategy {

    /** The [[SelectStrategy]] can be used to steer the outcome of a potential select call.
     *
     *  @param selectSupplier
     *    The supplier with the result of a select result.
     *  @param notBlockForIo
     *    true if blocking for IO is not allowed.
     *  @return
     *    [[SelectStrategy.SELECT]] if the next step should be blocking select [[SelectStrategy.CONTINUE]] if the next
     *    step should be to not select but rather jump back to the IO loop and try again. Any value >= 0 is treated as
     *    an indicator that work needs to be done.
     */
    @throws[Exception]
    def calculateStrategy(selectSupplier: IntSupplier, notBlockForIo: Boolean): Int
}
