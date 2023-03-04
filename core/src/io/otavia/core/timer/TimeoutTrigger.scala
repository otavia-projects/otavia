/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
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

package io.otavia.core.timer

import java.util.Date
import java.util.concurrent.TimeUnit

sealed trait TimeoutTrigger

object TimeoutTrigger {

    case class FixTime(date: Date) extends TimeoutTrigger

    case class DelayTime(delay: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) extends TimeoutTrigger

    case class DelayPeriod(
        delay: Long,
        period: Long,
        delayUnit: TimeUnit = TimeUnit.MILLISECONDS,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ) extends TimeoutTrigger

    case class FirstTimePeriod(first: Date, period: Long, periodUnit: TimeUnit = TimeUnit.MILLISECONDS)
        extends TimeoutTrigger

}
