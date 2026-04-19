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

import java.net.InetAddress
import scala.language.unsafeNulls

/** Immutable top-level configuration for an [[cc.otavia.core.system.ActorSystem]].
 *
 *  Built once via [[OtaviaConfigBuilder]] and passed to the actor system at creation time. All config groups are
 *  immutable case classes with sensible defaults.
 *
 *  Configuration values follow a priority chain: explicit builder value > system property (`-D`) > hardcoded default.
 *
 *  Example usage:
 *  {{{
 *  val system = ActorSystem("my-app") { builder =>
 *    builder.actorThreadPoolSize(4)
 *      .pageSize(8)
 *      .ioRatio(60)
 *      .withModuleConfig(SslConfig(cacheBufferSize = 32768))
 *  }
 *
 *  // Access config from anywhere
 *  system.config.buffer.pageSize
 *  system.config.scheduler.ioRatio
 *  }}}
 *
 *  @param name
 *    Name of the actor system. Used for logging and identification. Default is "ActorSystem:<hostname>".
 *  @param system
 *    System-level configuration: thread pool sizing, memory/system monitors, GC behavior.
 *  @param buffer
 *    Buffer allocation configuration: page size, allocator cache sizes.
 *  @param scheduler
 *    Actor scheduling configuration: IO ratio, time budgets, work stealing thresholds.
 *  @param reactor
 *    NIO reactor configuration: worker count, selector tuning.
 *  @param channel
 *    Channel defaults: connect timeout, write watermarks, inflight limits.
 *  @param timer
 *    Hashed wheel timer configuration: tick duration, wheel size.
 *  @param spinLock
 *    Spin lock tuning: spin/yield/park thresholds.
 *  @param priority
 *    Scheduling priority thresholds: mailbox sizes that trigger high priority.
 */
case class OtaviaConfig(
    name: String                                         = s"ActorSystem:${InetAddress.getLocalHost.getHostName}",
    system: ActorSystemConfig                            = ActorSystemConfig(),
    buffer: BufferConfig                                 = BufferConfig(),
    scheduler: SchedulerConfig                           = SchedulerConfig(),
    reactor: ReactorConfig                               = ReactorConfig(),
    channel: ChannelConfig                               = ChannelConfig(),
    timer: TimerConfig                                   = TimerConfig(),
    spinLock: SpinLockConfig                             = SpinLockConfig(),
    priority: PriorityConfig                             = PriorityConfig(),
    private val moduleConfigs: Map[Class[?], ModuleConfig] = Map.empty
) {

    /** Type-safe retrieval of a module config.
 *
 *  @tparam M
     *    The module config type.
     *  @param clazz
     *    The class of the module config to retrieve.
     *  @return
     *    [[Some]] containing the config if registered, [[None]] otherwise.
     */
    def getModuleConfig[M <: ModuleConfig](clazz: Class[M]): Option[M] =
        moduleConfigs.get(clazz).asInstanceOf[Option[M]]

    /** Type-safe retrieval of a module config with a default fallback.
 *
 *  @tparam M
     *    The module config type.
     *  @param clazz
     *    The class of the module config to retrieve.
     *  @param default
     *    Default value to return if not registered.
     *  @return
     *    The registered config, or the default if not found.
     */
    def moduleConfig[M <: ModuleConfig](clazz: Class[M])(default: => M): M =
        moduleConfigs.get(clazz).asInstanceOf[Option[M]].getOrElse(default)

}

object OtaviaConfig {

    /** Create a default config that reads all values from system properties with hardcoded fallbacks. */
    def apply(): OtaviaConfig = builder().build()

    /** Create a builder for constructing a custom configuration. */
    def builder(): OtaviaConfigBuilder = OtaviaConfigBuilder()

}
