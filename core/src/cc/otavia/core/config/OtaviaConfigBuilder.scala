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

import cc.otavia.common.{Report, SystemPropertyUtil}

import scala.collection.mutable
import scala.language.unsafeNulls

/** Builder for constructing [[OtaviaConfig]] instances.
 *
 *  Provides a fluent API for configuring the actor system. All setter methods return `this` for chaining.
 *
 *  Configuration values follow a priority chain: explicit builder value > system property (`-D`) > hardcoded default.
 *
 *  Example:
 *  {{{
 *  val config = OtaviaConfigBuilder()
 *    .name("my-system")
 *    .actorThreadPoolSize(4)
 *    .pageSize(8)
 *    .ioRatio(60)
 *    .withModuleConfig(SslConfig(cacheBufferSize = 32768))
 *    .build()
 *
 *  val system = ActorSystem(config)
 *  }}}
 *
 *  For convenience, you can also use the callback style:
 *  {{{
 *  val system = ActorSystem("my-system") { builder =>
 *    builder.actorThreadPoolSize(4).ioRatio(60)
 *  }
 *  }}}
 */
class OtaviaConfigBuilder private () {

    private var _name: Option[String]                              = None
    private val _system: ActorSystemConfigBuilder                  = ActorSystemConfigBuilder()
    private val _buffer: BufferConfigBuilder                       = BufferConfigBuilder()
    private val _scheduler: SchedulerConfigBuilder                 = SchedulerConfigBuilder()
    private val _reactor: ReactorConfigBuilder                     = ReactorConfigBuilder()
    private val _channel: ChannelConfigBuilder                     = ChannelConfigBuilder()
    private val _timer: TimerConfigBuilder                         = TimerConfigBuilder()
    private val _spinLock: SpinLockConfigBuilder                   = SpinLockConfigBuilder()
    private val _priority: PriorityConfigBuilder                   = PriorityConfigBuilder()
    private val _moduleConfigs: mutable.Map[Class[?], ModuleConfig] = mutable.Map.empty

    // --- Top-level setters ---

    def name(value: String): this.type = { _name = Some(value); this }

    // --- Convenience setters for the most common options ---

    def actorThreadPoolSize(value: Int): this.type = { _system.actorThreadPoolSize(value); this }

    def pageSize(kb: Int): this.type = { _buffer.pageSize(kb); this }

    def ioRatio(value: Int): this.type = { _scheduler.ioRatio(value); this }

    // --- Sub-builder accessors ---

    def system: ActorSystemConfigBuilder   = _system
    def buffer: BufferConfigBuilder        = _buffer
    def scheduler: SchedulerConfigBuilder  = _scheduler
    def reactor: ReactorConfigBuilder      = _reactor
    def channel: ChannelConfigBuilder      = _channel
    def timer: TimerConfigBuilder          = _timer
    def spinLock: SpinLockConfigBuilder    = _spinLock
    def priority: PriorityConfigBuilder    = _priority

    // --- Module config registration ---

    def withModuleConfig[M <: ModuleConfig](config: M): this.type = {
        _moduleConfigs(config.getClass) = config
        this
    }

    // --- Build ---

    def build(): OtaviaConfig = {
        val resolvedName = _name.getOrElse {
            s"ActorSystem:${java.net.InetAddress.getLocalHost.getHostName}"
        }
        OtaviaConfig(
            name          = resolvedName,
            system        = _system.build(),
            buffer        = _buffer.build(),
            scheduler     = _scheduler.build(),
            reactor       = _reactor.build(),
            channel       = _channel.build(),
            timer         = _timer.build(),
            spinLock      = _spinLock.build(),
            priority      = _priority.build(),
            moduleConfigs = _moduleConfigs.toMap
        )
    }

}

object OtaviaConfigBuilder {
    def apply(): OtaviaConfigBuilder = new OtaviaConfigBuilder()
}

// ============================================================================
// Sub-builders
// ============================================================================

private object SysPropResolver {
    def intOpt(key: String): Option[Int] =
        SystemPropertyUtil.get(key).map(_.toInt)

    def booleanOpt(key: String): Option[Boolean] =
        SystemPropertyUtil.get(key).map(_.toBoolean)

    def longOpt(key: String): Option[Long] =
        SystemPropertyUtil.get(key).map(_.toLong)

    def floatOpt(key: String): Option[Float] =
        SystemPropertyUtil.get(key).map(_.toFloat)
}

// --- ActorSystemConfig ---

class ActorSystemConfigBuilder {
    private var _actorThreadPoolSize: Option[Int]    = None
    private var _memoryMonitor: Option[Boolean]      = None
    private var _memoryMonitorDurationMs: Option[Int] = None
    private var _memoryOverSleepMs: Option[Int]      = None
    private var _systemMonitor: Option[Boolean]      = None
    private var _systemMonitorDurationMs: Option[Int] = None
    private var _printBanner: Option[Boolean]        = None
    private var _aggressiveGC: Option[Boolean]       = None
    private var _maxBatchSize: Option[Int]           = None
    private var _maxFetchPerRunning: Option[Int]     = None
    private var _poolHolderMaxSize: Option[Int]      = None

    def actorThreadPoolSize(value: Int): this.type = { _actorThreadPoolSize = Some(value); this }
    def memoryMonitor(value: Boolean): this.type = { _memoryMonitor = Some(value); this }
    def memoryMonitorDurationMs(value: Int): this.type = { _memoryMonitorDurationMs = Some(value); this }
    def memoryOverSleepMs(value: Int): this.type = { _memoryOverSleepMs = Some(value); this }
    def systemMonitor(value: Boolean): this.type = { _systemMonitor = Some(value); this }
    def systemMonitorDurationMs(value: Int): this.type = { _systemMonitorDurationMs = Some(value); this }
    def printBanner(value: Boolean): this.type = { _printBanner = Some(value); this }
    def aggressiveGC(value: Boolean): this.type = { _aggressiveGC = Some(value); this }
    def maxBatchSize(value: Int): this.type = { _maxBatchSize = Some(value); this }
    def maxFetchPerRunning(value: Int): this.type = { _maxFetchPerRunning = Some(value); this }
    def poolHolderMaxSize(value: Int): this.type = { _poolHolderMaxSize = Some(value); this }

    private[config] def build(): ActorSystemConfig = {
        val defaultCpus = Runtime.getRuntime.availableProcessors()
        val rawPoolSize = _actorThreadPoolSize
            .orElse(SysPropResolver.intOpt("cc.otavia.actor.worker.size"))
            .orElse(
                SysPropResolver.floatOpt("cc.otavia.actor.worker.ratio")
                    .map(r => (r * defaultCpus).toInt)
            )
            .getOrElse(defaultCpus)
        val poolSize =
            if (rawPoolSize < 1) {
                Report.report(s"cc.otavia.actor.worker.size can't set $rawPoolSize, it must >= 1, set $defaultCpus default")
                defaultCpus
            } else rawPoolSize

        val durationMs = _memoryMonitorDurationMs
            .orElse(SysPropResolver.intOpt("cc.otavia.system.memory.monitor.duration"))
            .getOrElse(20) * 100

        val sysMonDurationMs = _systemMonitorDurationMs
            .orElse(SysPropResolver.intOpt("cc.otavia.system.monitor.duration"))
            .getOrElse(10) * 100

        ActorSystemConfig(
            actorThreadPoolSize   = poolSize,
            memoryMonitor         = _memoryMonitor
                .orElse(SysPropResolver.booleanOpt("cc.otavia.system.memory.monitor"))
                .getOrElse(true),
            memoryMonitorDurationMs = durationMs,
            memoryOverSleepMs     = _memoryOverSleepMs
                .orElse(SysPropResolver.intOpt("cc.otavia.system.memory.over.sleep"))
                .getOrElse(40),
            systemMonitor         = _systemMonitor
                .orElse(SysPropResolver.booleanOpt("cc.otavia.system.monitor"))
                .getOrElse(false),
            systemMonitorDurationMs = sysMonDurationMs,
            printBanner           = _printBanner
                .orElse(SysPropResolver.booleanOpt("cc.otavia.system.banner"))
                .getOrElse(true),
            aggressiveGC          = _aggressiveGC
                .orElse(SysPropResolver.booleanOpt("cc.otavia.system.gc.aggressive"))
                .getOrElse(true),
            maxBatchSize          = _maxBatchSize
                .getOrElse(100000),
            maxFetchPerRunning    = _maxFetchPerRunning
                .getOrElse(Int.MaxValue),
            poolHolderMaxSize     = _poolHolderMaxSize
                .orElse(SysPropResolver.intOpt("cc.otavia.pool.holder.maxSize"))
                .getOrElse(256)
        )
    }
}

// --- BufferConfig ---

class BufferConfigBuilder {
    private var _pageSize: Option[Int] = None
    private var _minCache: Option[Int] = None
    private var _maxCache: Option[Int] = None

    /** Set page size in KB. Must be one of {1, 2, 4, 8, 16}. */
    def pageSize(kb: Int): this.type = { _pageSize = Some(kb); this }
    def minCache(value: Int): this.type = { _minCache = Some(value); this }
    def maxCache(value: Int): this.type = { _maxCache = Some(value); this }

    private[config] def build(): BufferConfig = {
        val enablePageSizes = Array(1, 2, 4, 8, 16)
        val kb = _pageSize
            .orElse(SysPropResolver.intOpt("cc.otavia.buffer.page.size"))
            .getOrElse(4)
        val pageSize =
            if (enablePageSizes.contains(kb)) kb * 1024
            else {
                Report.report(
                    s"cc.otavia.buffer.page.size is set to $kb, but only support ${enablePageSizes.mkString("[", ", ", "]")}, set to default ${4 * 1024}",
                    "Buffer"
                )
                4 * 1024
            }

        val minCache = {
            val raw = _minCache
                .orElse(SysPropResolver.intOpt("cc.otavia.buffer.allocator.cache.min"))
                .getOrElse(8)
            if (raw < 1) {
                Report.report(
                    s"cc.otavia.buffer.allocator.cache.min can't set $raw, it must large than 1, set 8 default"
                )
                8
            } else raw
        }

        val maxCache = {
            val raw = _maxCache
                .orElse(SysPropResolver.intOpt("cc.otavia.buffer.allocator.cache.max"))
                .getOrElse(10240)
            if (raw < minCache) {
                Report.report(
                    s"cc.otavia.buffer.allocator.cache.max can't set $raw, it must large than min cache = $minCache, set $minCache default"
                )
                minCache
            } else raw
        }

        BufferConfig(pageSize = pageSize, minCache = minCache, maxCache = maxCache)
    }
}

// --- SchedulerConfig ---

class SchedulerConfigBuilder {
    private var _ioRatio: Option[Int]           = None
    private var _minBudgetMicros: Option[Int]   = None
    private var _stealFloor: Option[Int]        = None
    private var _stealAggression: Option[Int]   = None

    def ioRatio(value: Int): this.type = { _ioRatio = Some(value); this }
    def minBudgetMicros(value: Int): this.type = { _minBudgetMicros = Some(value); this }
    def stealFloor(value: Int): this.type = { _stealFloor = Some(value); this }
    def stealAggression(value: Int): this.type = { _stealAggression = Some(value); this }

    private[config] def build(): SchedulerConfig = {
        val rawIoRatio = _ioRatio
            .orElse(SysPropResolver.intOpt("cc.otavia.actor.io.ratio"))
            .getOrElse(50)
        val ioRatio =
            if (rawIoRatio <= 0 || rawIoRatio > 100) {
                Report.report(s"cc.otavia.actor.io.ratio can't set $rawIoRatio, it must in (0, 100], set 50 default")
                50
            } else rawIoRatio

        val rawMinBudget = _minBudgetMicros
            .orElse(SysPropResolver.intOpt("cc.otavia.actor.min.budget.microsecond"))
            .getOrElse(500)
        val minBudgetMicros =
            if (rawMinBudget < 1) {
                Report.report(s"cc.otavia.actor.min.budget.microsecond can't set $rawMinBudget, it must >= 1, set 500 default")
                500
            } else rawMinBudget

        val rawStealFloor = _stealFloor
            .orElse(SysPropResolver.intOpt("cc.otavia.core.steal.floor"))
            .getOrElse(32)
        val stealFloor =
            if (rawStealFloor < 1) {
                Report.report(s"cc.otavia.core.steal.floor can't set $rawStealFloor, it must >= 1, set 32 default")
                32
            } else rawStealFloor

        val rawStealAggression = _stealAggression
            .orElse(SysPropResolver.intOpt("cc.otavia.core.steal.aggression"))
            .getOrElse(128)
        val stealAggression =
            if (rawStealAggression <= stealFloor) {
                Report.report(s"cc.otavia.core.steal.aggression can't set $rawStealAggression, it must > stealFloor($stealFloor), set 128 default")
                128
            } else rawStealAggression

        SchedulerConfig(
            ioRatio = ioRatio,
            minBudgetMicros = minBudgetMicros,
            stealFloor = stealFloor,
            stealAggression = stealAggression
        )
    }
}

// --- ReactorConfig ---

class ReactorConfigBuilder {
    private var _maxTasksPerRun: Option[Int]                 = None
    private var _nioWorkerSize: Option[Int]                  = None
    private var _selectorAutoRebuildThreshold: Option[Int]   = None
    private var _noKeySetOptimization: Option[Boolean]       = None

    def maxTasksPerRun(value: Int): this.type = { _maxTasksPerRun = Some(value); this }
    def nioWorkerSize(value: Int): this.type = { _nioWorkerSize = Some(value); this }
    def selectorAutoRebuildThreshold(value: Int): this.type = { _selectorAutoRebuildThreshold = Some(value); this }
    def noKeySetOptimization(value: Boolean): this.type = { _noKeySetOptimization = Some(value); this }

    private[config] def build(): ReactorConfig = {
        val defaultCpus = Runtime.getRuntime.availableProcessors()
        val rawNioSize = _nioWorkerSize
            .orElse(SysPropResolver.intOpt("cc.otavia.nio.worker.size"))
            .orElse(
                SysPropResolver.floatOpt("cc.otavia.nio.worker.ratio")
                    .map(r => (r * defaultCpus).toInt)
            )
            .getOrElse(defaultCpus)
        val nioSize =
            if (rawNioSize < 1) {
                Report.report(s"cc.otavia.nio.worker.size can't set $rawNioSize, it must >= 1, set $defaultCpus default")
                defaultCpus
            } else rawNioSize

        ReactorConfig(
            maxTasksPerRun = _maxTasksPerRun
                .orElse(SysPropResolver.intOpt("cc.otavia.reactor.maxTaskPerRun"))
                .map(m => Math.max(1, m))
                .getOrElse(16),
            nioWorkerSize = nioSize,
            selectorAutoRebuildThreshold = _selectorAutoRebuildThreshold
                .orElse(SysPropResolver.intOpt("io.otavia.selectorAutoRebuildThreshold"))
                .orElse(SysPropResolver.intOpt("io.netty5.selectorAutoRebuildThreshold"))
                .getOrElse(512),
            noKeySetOptimization = _noKeySetOptimization
                .orElse(SysPropResolver.booleanOpt("io.netty5.noKeySetOptimization"))
                .getOrElse(false)
        )
    }
}

// --- ChannelConfig ---

class ChannelConfigBuilder {
    private var _connectTimeoutMs: Option[Int]   = None
    private var _writeWaterMarkLow: Option[Int]  = None
    private var _writeWaterMarkHigh: Option[Int] = None
    private var _maxFutureInflight: Option[Int]  = None
    private var _maxStackInflight: Option[Int]   = None
    private var _maxMessagesPerRead: Option[Int] = None

    def connectTimeoutMs(value: Int): this.type = { _connectTimeoutMs = Some(value); this }
    def writeWaterMarkLow(value: Int): this.type = { _writeWaterMarkLow = Some(value); this }
    def writeWaterMarkHigh(value: Int): this.type = { _writeWaterMarkHigh = Some(value); this }
    def maxFutureInflight(value: Int): this.type = { _maxFutureInflight = Some(value); this }
    def maxStackInflight(value: Int): this.type = { _maxStackInflight = Some(value); this }
    def maxMessagesPerRead(value: Int): this.type = { _maxMessagesPerRead = Some(value); this }

    private[config] def build(): ChannelConfig = {
        val rawLow  = _writeWaterMarkLow.getOrElse(32768)
        val rawHigh = _writeWaterMarkHigh.getOrElse(65536)
        val (writeWaterMarkLow, writeWaterMarkHigh) =
            if (rawLow < 1 || rawHigh < 1 || rawLow >= rawHigh) {
                Report.report(s"cc.otavia.channel.writeWaterMark[$rawLow, $rawHigh] invalid, must satisfy 1 <= low < high, set [32768, 65536] default")
                (32768, 65536)
            } else (rawLow, rawHigh)

        ChannelConfig(
            connectTimeoutMs   = _connectTimeoutMs.getOrElse(30000),
            writeWaterMarkLow  = writeWaterMarkLow,
            writeWaterMarkHigh = writeWaterMarkHigh,
            maxFutureInflight  = _maxFutureInflight.getOrElse(1),
            maxStackInflight   = _maxStackInflight.getOrElse(1),
            maxMessagesPerRead = _maxMessagesPerRead.getOrElse(16)
        )
    }
}

// --- TimerConfig ---

class TimerConfigBuilder {
    private var _tickDurationMs: Option[Long] = None
    private var _ticksPerWheel: Option[Int]   = None

    def tickDurationMs(value: Long): this.type = { _tickDurationMs = Some(value); this }
    def ticksPerWheel(value: Int): this.type = { _ticksPerWheel = Some(value); this }

    private[config] def build(): TimerConfig = {
        val rawTickDuration = _tickDurationMs.getOrElse(100L)
        val tickDurationMs =
            if (rawTickDuration < 1) {
                Report.report(s"cc.otavia.timer.tickDurationMs can't set $rawTickDuration, it must >= 1, set 100 default")
                100L
            } else rawTickDuration

        val rawTicksPerWheel = _ticksPerWheel.getOrElse(512)
        val ticksPerWheel =
            if (rawTicksPerWheel < 1) {
                Report.report(s"cc.otavia.timer.ticksPerWheel can't set $rawTicksPerWheel, it must >= 1, set 512 default")
                512
            } else rawTicksPerWheel

        TimerConfig(tickDurationMs = tickDurationMs, ticksPerWheel = ticksPerWheel)
    }
}

// --- SpinLockConfig ---

class SpinLockConfigBuilder {
    private var _spinThreshold: Option[Int]  = None
    private var _yieldThreshold: Option[Int] = None
    private var _parkNanos: Option[Long]     = None

    def spinThreshold(value: Int): this.type = { _spinThreshold = Some(value); this }
    def yieldThreshold(value: Int): this.type = { _yieldThreshold = Some(value); this }
    def parkNanos(value: Long): this.type = { _parkNanos = Some(value); this }

    private[config] def build(): SpinLockConfig = SpinLockConfig(
        spinThreshold  = _spinThreshold.getOrElse(100),
        yieldThreshold = _yieldThreshold.getOrElse(200),
        parkNanos      = _parkNanos.getOrElse(1000L)
    )
}

// --- PriorityConfig ---

class PriorityConfigBuilder {
    private var _highPriorityReplySize: Option[Int] = None
    private var _highPriorityEventSize: Option[Int] = None

    def highPriorityReplySize(value: Int): this.type = { _highPriorityReplySize = Some(value); this }
    def highPriorityEventSize(value: Int): this.type = { _highPriorityEventSize = Some(value); this }

    private[config] def build(): PriorityConfig = {
        val rawReplySize = _highPriorityReplySize.getOrElse(2)
        val highPriorityReplySize =
            if (rawReplySize < 1) {
                Report.report(s"cc.otavia.priority.highPriorityReplySize can't set $rawReplySize, it must >= 1, set 2 default")
                2
            } else rawReplySize

        val rawEventSize = _highPriorityEventSize.getOrElse(4)
        val highPriorityEventSize =
            if (rawEventSize < 1) {
                Report.report(s"cc.otavia.priority.highPriorityEventSize can't set $rawEventSize, it must >= 1, set 4 default")
                4
            } else rawEventSize

        PriorityConfig(highPriorityReplySize = highPriorityReplySize, highPriorityEventSize = highPriorityEventSize)
    }
}
