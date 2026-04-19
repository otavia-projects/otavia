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

/** Actor scheduling configuration for the event loop.
 *
 *  @param ioRatio
 *    Percentage of event loop time allocated to IO (0-100). The remaining time is allocated to StateActor execution.
 *    Default is 50 (equal time for IO and actors). Set to 100 to disable time budgeting for actors.
 *  @param minBudgetMicros
 *    Minimum time budget in microseconds for StateActor execution when there are no IO events. Prevents actor
 *    starvation when the system is idle from an IO perspective. Default is 500μs.
 *  @param stealFloor
 *    Minimum victim queue depth to consider work stealing. Below this threshold the owner thread will self-drain
 *    quickly, and the CPU cache cost of cross-thread execution outweighs the benefit. Default is 32.
 *  @param stealAggression
 *    Product threshold for the adaptive steal condition: `idleCount × readies >= stealAggression`. Higher values
 *    make stealing more conservative (require more idle iterations or deeper backlog). Default is 128.
 */
case class SchedulerConfig(
    ioRatio: Int          = 50,
    minBudgetMicros: Int  = 500,
    stealFloor: Int       = 32,
    stealAggression: Int  = 128
)
