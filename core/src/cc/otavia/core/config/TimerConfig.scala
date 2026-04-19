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

package cc.otavia.core.config

/** Hashed wheel timer configuration.
 *
 *  The timer uses a hashed wheel data structure for efficient timeout scheduling with O(1) add/cancel operations.
 *  Based on George Varghese and Tony Lauck's paper "Hashed and Hierarchical Timing Wheels".
 *
 *  @param tickDurationMs
 *    Duration in milliseconds between timer ticks. The timer checks for expired timeouts on each tick. Smaller
 *    values provide better accuracy but increase CPU overhead. In most network applications, I/O timeouts do not
 *    need to be precise. Default is 100ms.
 *  @param ticksPerWheel
 *    Number of slots in the timing wheel (wheel size). Larger wheels can schedule more timeouts with less hash
 *    collisions but consume more memory. Must be a power of 2. Default is 512.
 */
case class TimerConfig(
    tickDurationMs: Long = 100,
    ticksPerWheel: Int   = 512
)
