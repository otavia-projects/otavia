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

package cc.otavia.core.transport.reactor.nio

import cc.otavia.buffer.pool.RecyclablePageBuffer
import cc.otavia.common.{SystemPropertyUtil, ThrowableUtil}
import cc.otavia.core.channel.*
import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.message.*
import cc.otavia.core.reactor.*
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.transport.nio.channel.{AbstractNioUnsafeChannel, NioUnsafeChannel}
import cc.otavia.core.transport.reactor.nio.NioHandler.*
import cc.otavia.internal.{Platform, ReflectionUtil}

import java.io.{IOException, UncheckedIOException}
import java.net.SocketAddress
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{CancelledKeyException, ClosedChannelException, SelectionKey, Selector}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import java.util
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.IntSupplier
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
                Class.forName("sun.nio.ch.SelectorImpl", false, Platform.getSystemClassLoader)
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
                            if (Platform.hasUnsafe) {
                                // Let us try to use sun.misc.Unsafe to replace the SelectionKeySet.
                                // This allows us to also do this in Java9+ without any extra flags.
                                val selectedKeysFieldOffset: Long =
                                    Platform.objectFieldOffset(selectedKeysField)
                                val publicSelectedKeysFieldOffset: Long =
                                    Platform.objectFieldOffset(publicSelectedKeysField)

                                if (selectedKeysFieldOffset != -1 && publicSelectedKeysFieldOffset != -1) {
                                    Platform.putObject(
                                      unwrappedSelector,
                                      selectedKeysFieldOffset,
                                      selectedKeySet
                                    )
                                    Platform.putObject(
                                      unwrappedSelector,
                                      publicSelectedKeysFieldOffset,
                                      selectedKeySet
                                    )
                                    success = true
                                }
                            }
                            if (!success) {
                                ReflectionUtil.trySetAccessible(selectedKeysField, true) match
                                    case Some(cause) => throw cause
                                    case None        =>

                                ReflectionUtil.trySetAccessible(publicSelectedKeysField, true) match
                                    case Some(cause) => throw cause
                                    case None        =>

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
                    val processor = key.attachment().asInstanceOf[NioUnsafeChannel]
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

    override def run(context: IoExecutionContext): Int = {
        var handled = 0
        try {
            select(context)
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

    private def processSelectedKeys(): Int = if (selectedKeys != null && selectedKeys.size() > 0)
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
        var i       = keys.size() - 1
        while (i >= 0) {
            val key = keys.keys(i)
            // null out entry in the array to allow to have it GC'ed once the Channel close
            // See https://github.com/netty/netty/issues/2363
            keys.keys(i) = null
            keys._size -= 1

            processSelectedKey(key)
            handled += 1

            if (needsToSelectAgain) {
                // null out entries in the array to allow to have it GC'ed once the Channel close
                // See https://github.com/netty/netty/issues/2363
                keys.reset()
                selectAgain()
                i = keys.size()
            }
            i -= 1
        }

        handled
    }

    private def processSelectedKey(key: SelectionKey): Unit = {
        val processor = key.attachment().asInstanceOf[NioUnsafeChannel]
        processor.handle(key)
    }

    override def prepareToDestroy(): Unit = {
        selectAgain()
        val keys = selector.keys()
        keys.forEach { key =>
            val processor = key.attachment().asInstanceOf[NioUnsafeChannel]
            processor.closeProcessor()
        }
    }

    override def register(channel: AbstractChannel): Unit = {
        val nioUnsafeChannel  = nioUnsafe(channel)
        var selected: Boolean = false
        var success: Boolean  = false
        while (!success) {
            try {
                nioUnsafeChannel.registerSelector(unwrappedSelector)
                // channel.invokeLater(() => channel.handleChannelRegisterReply(nioUnsafeChannel.isActive, None))
                channel.executorAddress.inform(
                  RegisterReply(channel, nioUnsafeChannel.isActive)
                )
                success = true
            } catch {
                case e: CancelledKeyException =>
                    if (!selected) {
                        selectNow()
                        selected = true
                    } else {
                        channel.executorAddress.inform(RegisterReply(channel, cause = Some(e)))
                        // channel.invokeLater(() => channel.handleChannelRegisterReply(false, Some(e)))
                    }
                case e: ClosedChannelException =>
                    // channel.invokeLater(() => channel.handleChannelRegisterReply(false, Some(e)))
                    channel.executorAddress.inform(RegisterReply(channel, cause = Some(e)))
                    success = true
            }
        }
    }

    override def deregister(channel: AbstractChannel): Unit = {
        val nioUnsafeChannel = nioUnsafe(channel)
        nioUnsafeChannel.deregisterSelector()
        cancelledKeys += 1
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0
            needsToSelectAgain = true
        }
    }

    override def bind(channel: AbstractChannel, local: SocketAddress): Unit = {
        try {
            channel.unsafeChannel.unsafeBind(local)
        } catch {
            case t: Throwable =>
                channel.executorAddress.inform(BindReply(channel, cause = Some(t)))
        }
    }

    override def open(
        channel: AbstractChannel,
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]]
    ): Unit = {
        try {
            channel.unsafeChannel.unsafeOpen(path, options, attrs)
        } catch {
            case t: Throwable =>
                channel.executorAddress.inform(OpenReply(channel, Some(t)))
        }
    }

    override def connect(
        channel: AbstractChannel,
        remote: SocketAddress,
        local: Option[SocketAddress],
        fastOpen: Boolean
    ): Unit = {
        try {
            channel.unsafeChannel.unsafeConnect(remote, local, fastOpen) // non-blocking
        } catch {
            case t: Throwable =>
                channel.executorAddress.inform(ConnectReply(channel, cause = Some(t)))
        }
    }

    override def disconnect(channel: AbstractChannel): Unit = {
        try {
            channel.unsafeChannel.unsafeDisconnect()
        } catch {
            case t: Throwable => channel.executorAddress.inform(DisconnectReply(channel, Some(t)))
        }
    }

    override def shutdown(channel: AbstractChannel, direction: ChannelShutdownDirection): Unit = ???

    override def close(channel: AbstractChannel): Unit = {
        try {
            channel.unsafeChannel.unsafeClose(None)
        } catch {
            case t: Throwable => channel.executorAddress.inform(ChannelClose(channel, Some(t)))
        }
    }

    override def read(channel: AbstractChannel, plan: ReadPlan): Unit = {
        channel.unsafeChannel.unsafeRead(plan)
    }

    override def flush(channel: AbstractChannel, payload: FileRegion | RecyclablePageBuffer): Unit =
        channel.unsafeChannel.unsafeFlush(payload)

    override def wakeup(): Unit =
        selector.wakeup()

    override def isCompatible(handleType: Class[? <: AbstractChannel]): Boolean = ???
    // classOf[AbstractNioChannel[?, ?]].isAssignableFrom(handleType)

    @throws[IOException]
    private def selectNow(): Int = selector.selectNow()

    private def select(context: IoExecutionContext): Unit = {
        try {
            if (context.canNotBlock) {
                // If a task was submitted when wakenUp value was true, the task didn't get a chance to call
                // Selector#wakeup. So we need to check task queue again before executing select operation.
                // If we don't, the task might be pended until select operation was timed out.
                // It might be pended until idle timeout if IdleStateHandler existed in pipeline.
                selector.selectNow()
            } else {
                selector.select(10)
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

    private def nioUnsafe(handle: Channel): NioUnsafeChannel = handle.unsafeChannel match
        case unsafe: AbstractNioUnsafeChannel[?] => unsafe
        case _ =>
            throw new IllegalArgumentException(s"Channel of type ${handle.getClass.getSimpleName} not supported")

}
