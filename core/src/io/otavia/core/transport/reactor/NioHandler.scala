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

package io.otavia.core.transport.reactor

import io.netty5.util.internal.*
import io.otavia.core.channel.{Channel, ChannelException}
import io.otavia.core.message.ReactorEvent
import io.otavia.core.reactor.*
import io.otavia.core.slf4a.Logger
import io.otavia.core.system.ActorSystem
import io.otavia.core.transport.nio.channel.{AbstractNioChannel, NioProcessor}
import io.otavia.core.transport.reactor.NioHandler.*

import java.io.{IOException, UncheckedIOException}
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{CancelledKeyException, SelectionKey, Selector}
import java.security.{AccessController, PrivilegedAction}
import java.util
import java.util.Set
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.IntSupplier
import scala.jdk.CollectionConverters
import scala.language.unsafeNulls
import scala.util.*

final class NioHandler(val selectorProvider: SelectorProvider, val selectStrategy: SelectStrategy, sys: ActorSystem)
    extends IoHandler(sys) {

    def this(system: ActorSystem) =
        this(SelectorProvider.provider(), DefaultSelectStrategyFactory.newSelectStrategy(), system)

    private val logger: Logger = Logger.getLogger(getClass, sys)

    private val selectNowSupplier: IntSupplier = new IntSupplier {
        override def getAsInt: Int = try { selectNow() }
        catch { case e: IOException => throw new UncheckedIOException(e) }
    }

    private val selectorTuple: SelectorTuple = openSelector()

    private var selector: Selector                    = selectorTuple.selector
    private var unwrappedSelector: Selector           = selectorTuple.unwrappedSelector
    private var selectedKeys: SelectedSelectionKeySet = _

    /** Boolean that controls determines if a blocked Selector.select should break out of its selection process. In our
     *  case we use a timeout for the select method and the select method will block for that time unless waken up.
     */
    private val wakenUp = new AtomicBoolean()

    private var cancelledKeys      = 0
    private var needsToSelectAgain = false

    private def openSelector(): SelectorTuple = {
        val unwrappedSelector =
            try {
                selectorProvider.openSelector()
            } catch {
                case e: IOException => throw new ChannelException(s"failed to open a new selector $e")
            }
        if (DISABLE_KEY_SET_OPTIMIZATION) SelectorTuple(unwrappedSelector)
        else {
            Try {
                Class.forName("sun.nio.ch.SelectorImpl", false, PlatformDependent.getSystemClassLoader)
            } match
                case Failure(throwable) =>
                    val msg = s"failed to instrument a special java.util.Set into: $unwrappedSelector $throwable"
                    logger.trace(msg)
                    SelectorTuple(unwrappedSelector)
                case Success(selectorImplClass) =>
                    if (!selectorImplClass.isAssignableFrom(unwrappedSelector.getClass))
                        SelectorTuple(unwrappedSelector)
                    else {
                        val selectedKeySet = new SelectedSelectionKeySet()
                        Try {
                            val selectedKeysField       = selectorImplClass.getDeclaredField("selectedKeys")
                            val publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys")
                            var success: Boolean        = false
                            if (PlatformDependent.hasUnsafe) {
                                // Let us try to use sun.misc.Unsafe to replace the SelectionKeySet.
                                // This allows us to also do this in Java9+ without any extra flags.
                                val selectedKeysFieldOffset: Long =
                                    PlatformDependent.objectFieldOffset(selectedKeysField)
                                val publicSelectedKeysFieldOffset: Long =
                                    PlatformDependent.objectFieldOffset(publicSelectedKeysField)

                                if (selectedKeysFieldOffset != -1 && publicSelectedKeysFieldOffset != -1) {
                                    PlatformDependent.putObject(
                                      unwrappedSelector,
                                      selectedKeysFieldOffset,
                                      selectedKeySet
                                    )
                                    PlatformDependent.putObject(
                                      unwrappedSelector,
                                      publicSelectedKeysFieldOffset,
                                      selectedKeySet
                                    )
                                    success = true
                                }
                            }
                            if (!success) {
                                val cause1 = ReflectionUtil.trySetAccessible(selectedKeysField, true)
                                if (cause1 != null) throw cause1

                                val cause2 = ReflectionUtil.trySetAccessible(publicSelectedKeysField, true)
                                if (cause2 != null) throw cause2

                                selectedKeysField.set(unwrappedSelector, selectedKeySet)
                                publicSelectedKeysField.set(unwrappedSelector, selectedKeySet)
                            }
                        } match
                            case Failure(e) =>
                                val msg = s"failed to instrument a special java.util.Set into: $unwrappedSelector $e"
                                logger.trace(msg)
                                SelectorTuple(unwrappedSelector)
                            case Success(_) =>
                                selectedKeys = selectedKeySet
                                logger.trace(s"instrumented a special java.util.Set into: $unwrappedSelector")
                                SelectorTuple(
                                  unwrappedSelector,
                                  new SelectedSelectionKeySetSelector(unwrappedSelector, selectedKeySet)
                                )
                    }
        }
    }

    /** Replaces the current [[Selector]] of this event loop with newly created [[Selector]]s to work around the
     *  infamous epoll 100% CPU bug.
     */
    private def rebuildSelector(): Unit = {
        val oldSelector = selector
        Try { openSelector() } match
            case Failure(e) =>
                val msg = s"Failed to create a new Selector. ${ThrowableUtil.stackTraceToString(e)}"
                logger.warn(msg)
            case Success(newSelectorTuple) =>
                // Register all channels to the new Selector.
                var nChannels = 0
                oldSelector.keys().forEach { key =>
                    val processor = key.attachment().asInstanceOf[NioProcessor]
                    try {
                        if (!key.isValid || key.channel().keyFor(newSelectorTuple.unwrappedSelector) != null) {} else {
                            processor.registerSelector(newSelectorTuple.unwrappedSelector)
                            nChannels += 1
                        }
                    } catch {
                        case e: Exception =>
                            val trace = ThrowableUtil.stackTraceToString(e)
                            logger.warn(s"Failed to re-register a NioHandle to the new Selector. $trace")
                            processor.closeProcessor()
                    }
                }
                selector = newSelectorTuple.selector
                unwrappedSelector = newSelectorTuple.unwrappedSelector

                try {
                    // time to close the old selector as everything else is registered to the new one
                    oldSelector.close()
                } catch {
                    case t: Throwable =>
                        val msg = ThrowableUtil.stackTraceToString(t)
                        logger.warn(s"Failed to close the old Selector. $msg")
                }

                logger.info(s"Migrated $nChannels channel(s) to the new Selector.")

    }

    override def run(runner: IoExecutionContext): Int = {
        var handled = 0
        try {
            val strategy = selectStrategy.calculateStrategy(selectNowSupplier, !runner.canBlock)
            if (strategy == SelectStrategy.SELECT) { // can block to select ready io events.
                try {
                    select(runner, wakenUp.getAndSet(false))
                    if (wakenUp.get()) selector.wakeup()
                } catch {
                    case e: IOException =>
                        rebuildSelector()
                        handleLoopException(e)
                        handled = 0
                }
            }
            cancelledKeys = 0
            needsToSelectAgain = false
            handled = processSelectedKeys()
        } catch {
            case e: Error =>
                e.printStackTrace()
                throw e
            case t: Throwable => handleLoopException(t)
        }
        handled
    }

    private def handleLoopException(t: Throwable): Unit = {
        logger.warn(s"Unexpected exception in the selector loop. ${ThrowableUtil.stackTraceToString(t)}")
        // Prevent possible consecutive immediate failures that lead to
        // excessive CPU consumption.
        try { Thread.sleep(1000) }
        catch { case e: InterruptedException => } // Ignore.
    }

    private def processSelectedKeys(): Int = if (selectedKeys ne null)
        processSelectedKeysOptimized()
    else
        processSelectedKeysPlain()

    override def destroy(): Unit = try { selector.close() }
    catch {
        case e: IOException =>
            logger.warn(s"Failed to close a selector. ${ThrowableUtil.stackTraceToString(e)}")
    }

    private def processSelectedKeysPlain(): Int = {
        var selectedKeys = selector.selectedKeys()
        // check if the set is empty and if so just return to not create garbage by
        // creating a new Iterator every time even if there is nothing to process.
        // See https://github.com/netty/netty/issues/597
        if (!selectedKeys.isEmpty) {
            var iterator = selectedKeys.iterator()
            var handled  = 0
            var break    = false
            while (!break) {
                val key = iterator.next()
                iterator.remove()
                processSelectedKey(key)
                handled += 1

                if (!iterator.hasNext) break = true

                if (needsToSelectAgain) {
                    selectAgain()
                    selectedKeys = selector.selectedKeys()

                    // Create the iterator again to avoid ConcurrentModificationException
                    if (selectedKeys.isEmpty) break = true else iterator = selectedKeys.iterator()
                }
            }
            handled
        } else 0
    }

    private def processSelectedKeysOptimized(): Int = {
        var handled = 0
        val keys    = selectedKeys
        var i       = 0
        val size    = keys.size()
        while (i < size) {
            val key = keys.keys(i)
            // null out entry in the array to allow to have it GC'ed once the Channel close
            // See https://github.com/netty/netty/issues/2363
            keys.keys(i) = null

            processSelectedKey(key)
            handled += 1

            if (needsToSelectAgain) {
                // null out entries in the array to allow to have it GC'ed once the Channel close
                // See https://github.com/netty/netty/issues/2363
                keys.reset(i + 1)
                selectAgain()
                i = -1
            }
            i += 1
        }

        handled
    }

    private def processSelectedKey(key: SelectionKey): Unit = {
        val processor = key.attachment().asInstanceOf[NioProcessor]
        processor.handle(key)
    }

    override def prepareToDestroy(): Unit = {
        selectAgain()
        val keys = selector.keys()
        keys.forEach { key =>
            val processor = key.attachment().asInstanceOf[NioProcessor]
            processor.closeProcessor()
        }
    }

    override def register(channel: Channel): Unit = {
        val nioProcessor      = nioHandle(channel)
        var selected: Boolean = false
        var success: Boolean  = false
        while (!success) {
            try {
                nioProcessor.registerSelector(unwrappedSelector)
                channel.executorAddress.inform(ReactorEvent.RegisterReply(channel))
                success = true
            } catch {
                case e: CancelledKeyException =>
                    if (!selected) {
                        selectNow()
                        selected = true
                    } else channel.executorAddress.inform(ReactorEvent.RegisterReply(channel, Some(e)))
            }
        }
    }

    override def deregister(channel: Channel): Unit = {
        val nioProcessor = nioHandle(channel)
        nioProcessor.deregisterSelector()
        cancelledKeys += 1
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0
            needsToSelectAgain = true
        }
    }

    override def wakeup(inEventLoop: Boolean): Unit = if (wakenUp.compareAndSet(false, true))
        selector.wakeup()

    override def isCompatible(handleType: Class[? <: Channel]): Boolean =
        classOf[AbstractNioChannel[?, ?]].isAssignableFrom(handleType)

    @throws[IOException]
    private def selectNow(): Int = try { selector.selectNow() }
    finally if (wakenUp.get()) selector.wakeup()

    @throws[IOException]
    private def select(runner: IoExecutionContext, oldWakeup: Boolean): Unit = {
        var selector = this.selector
        try {
            var selectCnt: Int      = 0
            var currentTimeNanos    = System.nanoTime
            val selectDeadLineNanos = currentTimeNanos + runner.delayNanos(currentTimeNanos)
            var break: Boolean      = false
            while (!break) {
                val timeoutMillis = (selectDeadLineNanos - currentTimeNanos + 500000L) / 1000000L
                if (timeoutMillis <= 0) {
                    if (selectCnt == 0) {
                        selector.selectNow
                        selectCnt = 1
                    }
                    break = true
                }

                // If a task was submitted when wakenUp value was true, the task didn't get a chance to call
                // Selector#wakeup. So we need to check task queue again before executing select operation.
                // If we don't, the task might be pended until select operation was timed out.
                // It might be pended until idle timeout if IdleStateHandler existed in pipeline.
                if (!runner.canBlock && wakenUp.compareAndSet(false, true)) {
                    selector.selectNow()
                    selectCnt = 1
                    break = true
                }

                val selectedKeys = selector.select(timeoutMillis)
                selectCnt += 1

                if (selectedKeys != 0 || oldWakeup || wakenUp.get() || !runner.canBlock) {
                    // - Selected something,
                    // - waken up by user, or
                    // - the task queue has a pending task.
                    // - a scheduled task is ready for processing
                    break = true
                }

                if (Thread.interrupted()) {
                    // Thread was interrupted so reset selected keys and break so we not run into a busy loop.
                    // As this is most likely a bug in the handler of the user or it's client library we will
                    // also log it.
                    //
                    // See https://github.com/netty/netty/issues/2426
                    logger.debug(
                      "Selector.select() returned prematurely because " +
                          "Thread.currentThread().interrupt() was called. Use " +
                          "NioHandler.shutdownGracefully() to shutdown the NioHandler."
                    )
                    selectCnt += 1
                    break = true
                }

                val time = System.nanoTime
                if (time - TimeUnit.MILLISECONDS.toNanos(timeoutMillis) >= currentTimeNanos) {
                    // timeoutMillis elapsed without anything selected.
                    selectCnt += 1
                } else if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 && selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
                    // The code exists in an extra method to ensure the method is not too big to inline as this
                    // branch is not very likely to get hit very frequently.
                    selector = selectRebuildSelector(selectCnt)
                    selectCnt = 1
                    break = true
                }

                currentTimeNanos = time
            }

            if (selectCnt > MIN_PREMATURE_SELECTOR_RETURNS) {
                logger.debug(
                  s"Selector.select() returned prematurely ${selectCnt - 1} times in a row for Selector $selector."
                )
            }
        } catch {
            case e: CancelledKeyException => // Harmless exception - log anyway
                val trace = ThrowableUtil.stackTraceToString(e)
                logger.debug(
                  s"${classOf[CancelledKeyException].getSimpleName} raised by a Selector $trace - JDK bug?"
                )
        }
    }

    @throws[IOException]
    private def selectRebuildSelector(selectCnt: Int): Selector = {
        // The selector returned prematurely many times in a row.
        // Rebuild the selector to work around the problem.
        logger.warn(
          s"Selector.select() returned prematurely $selectCnt times in a row; rebuilding Selector ${this.selector}."
        )
        rebuildSelector()
        val selector = this.selector

        // Select again to populate selectedKeys.
        selector.selectNow()

        selector
    }

    private def selectAgain(): Unit = try {
        needsToSelectAgain = false
        selector.selectNow()
    } catch {
        case t: Throwable =>
            val trace = ThrowableUtil.stackTraceToString(t)
            logger.warn(s"Failed to update SelectionKeys. $trace")
    }

}

object NioHandler {

    final protected case class SelectorTuple(unwrappedSelector: Selector, selector: Selector) {
        def this(unwrappedSelector: Selector) = this(unwrappedSelector, unwrappedSelector)
    }

    private object SelectorTuple {
        def apply(unwrappedSelector: Selector): SelectorTuple = SelectorTuple(unwrappedSelector, unwrappedSelector)
    }

    private val CLEANUP_INTERVAL = 256 // XXX Hard-coded value, but won't need customization.+

    private val DISABLE_KEY_SET_OPTIMIZATION =
        SystemPropertyUtil.getBoolean("io.netty5.noKeySetOptimization", false)

    private val MIN_PREMATURE_SELECTOR_RETURNS  = 3
    private var SELECTOR_AUTO_REBUILD_THRESHOLD = 0

    private var selectorAutoRebuildThreshold: Int =
        SystemPropertyUtil.getInt("io.netty5.selectorAutoRebuildThreshold", 512)
    if (selectorAutoRebuildThreshold < MIN_PREMATURE_SELECTOR_RETURNS) selectorAutoRebuildThreshold = 0

    SELECTOR_AUTO_REBUILD_THRESHOLD = selectorAutoRebuildThreshold

    private def nioHandle(handle: Channel): NioProcessor = handle match
        case channel: AbstractNioChannel[?, ?] => channel.nioProcessor
        case _ =>
            throw new IllegalArgumentException(s"Channel of type ${StringUtil.simpleClassName(handle)} not supported")

}
