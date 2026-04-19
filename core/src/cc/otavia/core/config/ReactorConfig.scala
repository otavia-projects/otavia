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

/** NIO reactor configuration.
 *
 *  @param maxTasksPerRun
 *    Maximum number of IO tasks processed per reactor loop iteration. Controls responsiveness vs throughput
 *    tradeoff. Default is 16.
 *  @param nioWorkerSize
 *    Number of NIO reactor worker threads. Defaults to available processors. These threads handle selector polling
 *    and channel IO events.
 *  @param selectorAutoRebuildThreshold
 *    Number of consecutive premature Selector returns before rebuilding the Selector to work around the epoll
 *    100% CPU bug. Set to 0 to disable auto-rebuild. Default is 512.
 *  @param noKeySetOptimization
 *    Disable the SelectedSelectionKeySet optimization that replaces the selector's internal key set with a
 *    more efficient implementation. May be needed on certain JVMs or for debugging. Default is false.
 */
case class ReactorConfig(
    maxTasksPerRun: Int               = 16,
    nioWorkerSize: Int                = Runtime.getRuntime.availableProcessors(),
    selectorAutoRebuildThreshold: Int = 512,
    noKeySetOptimization: Boolean     = false
)
