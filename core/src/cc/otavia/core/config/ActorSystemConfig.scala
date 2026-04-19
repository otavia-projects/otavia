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

import scala.language.unsafeNulls

/** System-level configuration for [[cc.otavia.core.system.ActorSystem]].
 *
 *  @param actorThreadPoolSize
 *    Number of ActorThread worker threads. Defaults to available processors.
 *  @param memoryMonitor
 *    Enable heap memory pressure monitoring. When enabled, the system tracks heap usage and marks itself as "busy"
 *    when memory is over 90% utilized.
 *  @param memoryMonitorDurationMs
 *    Interval in milliseconds between memory monitor checks. Default is 2000ms.
 *  @param memoryOverSleepMs
 *    Sleep time in milliseconds when system is busy and a non-actor thread sends a notice. Prevents non-actor threads
 *    from overwhelming the system during memory pressure.
 *  @param systemMonitor
 *    Enable system-wide metrics monitoring task. Collects runtime statistics for debugging/observability.
 *  @param systemMonitorDurationMs
 *    Interval in milliseconds between system monitor runs. Default is 1000ms.
 *  @param printBanner
 *    Print the Otavia ASCII banner on system startup.
 *  @param aggressiveGC
 *    Enable aggressive garbage collection behavior. When true, the system proactively triggers GC when memory pressure
 *    is detected.
 *  @param maxBatchSize
 *    Maximum number of messages processed in a single actor dispatch cycle. Prevents any single actor from monopolizing
 *    the thread indefinitely.
 *  @param maxFetchPerRunning
 *    Maximum number of messages fetched per actor run. Controls how many messages are dequeued before yielding.
 *  @param poolHolderMaxSize
 *    Maximum number of pooled objects per [[cc.otavia.core.cache.SingleThreadPoolableHolder]]. Controls memory vs
 *    allocation overhead tradeoff for object pools.
 */
case class ActorSystemConfig(
    actorThreadPoolSize: Int     = Runtime.getRuntime.availableProcessors(),
    memoryMonitor: Boolean       = true,
    memoryMonitorDurationMs: Int = 2000,
    memoryOverSleepMs: Int       = 40,
    systemMonitor: Boolean       = false,
    systemMonitorDurationMs: Int = 1000,
    printBanner: Boolean         = true,
    aggressiveGC: Boolean        = true,
    maxBatchSize: Int            = 100000,
    maxFetchPerRunning: Int      = Int.MaxValue,
    poolHolderMaxSize: Int       = 256
)
