/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.reactor.nio

import io.netty5.util.internal.*
import io.otavia.core.address.ChannelsActorAddress
import io.otavia.core.channel.nio.{AbstractNioChannel, NioProcessor}
import io.otavia.core.channel.{Channel, ChannelException}
import io.otavia.core.reactor.*
import io.otavia.core.reactor.nio.NioHandler.*
import org.log4s.{Logger, getLogger}

import java.io.{IOException, UncheckedIOException}
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{CancelledKeyException, Selector}
import java.security.{AccessController, PrivilegedAction}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.IntSupplier

final class NioHandler(val selectorProvider: SelectorProvider, val selectStrategy: SelectStrategy) extends IoHandler {

    def this() = this(SelectorProvider.provider(), DefaultSelectStrategyFactory.newSelectStrategy())

    val logger: Logger = getLogger

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
            val maybeSelectorImplClass = AccessController.doPrivileged(new PrivilegedAction[AnyRef] {
                override def run(): AnyRef = try {
                    Class.forName("sun.nio.ch.SelectorImpl", false, PlatformDependent.getSystemClassLoader)
                } catch {
                    case cause: Throwable => cause
                }
            })
            if (
              !(maybeSelectorImplClass.isInstanceOf[Class[?]]) ||
              !maybeSelectorImplClass.asInstanceOf[Class[?]].isAssignableFrom(unwrappedSelector.getClass)
            ) {
                maybeSelectorImplClass match {
                    case throwable: Throwable =>
                        logger.trace(
                          s"failed to instrument a special java.util.Set into: ${unwrappedSelector} ${throwable}"
                        )
                    case _ =>
                }
                SelectorTuple(unwrappedSelector)
            } else {
                val selectorImplClass: Class[?] = maybeSelectorImplClass.asInstanceOf[Class[?]]
                val selectedKeySet              = new SelectedSelectionKeySet()

                val maybeException = AccessController.doPrivileged(new PrivilegedAction[AnyRef] {
                    override def run(): AnyRef = try {
                        val selectedKeysField       = selectorImplClass.getDeclaredField("selectedKeys")
                        val publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys")

                        if (PlatformDependent.javaVersion() >= 9 && PlatformDependent.hasUnsafe) {
                            // Let us try to use sun.misc.Unsafe to replace the SelectionKeySet.
                            // This allows us to also do this in Java9+ without any extra flags.
                            val selectedKeysFieldOffset: Long = PlatformDependent.objectFieldOffset(selectedKeysField)
                            val publicSelectedKeysFieldOffset: Long =
                                PlatformDependent.objectFieldOffset(publicSelectedKeysField)

                            if (selectedKeysFieldOffset != -1 && publicSelectedKeysFieldOffset != -1) {
                                PlatformDependent.putObject(unwrappedSelector, selectedKeysFieldOffset, selectedKeySet)
                                PlatformDependent.putObject(
                                  unwrappedSelector,
                                  publicSelectedKeysFieldOffset,
                                  selectedKeySet
                                )
                                return null
                            }
                        }

                        var cause = ReflectionUtil.trySetAccessible(selectedKeysField, true)
                        if (cause != null) return cause

                        cause = ReflectionUtil.trySetAccessible(publicSelectedKeysField, true)
                        if (cause != null) return cause

                        selectedKeysField.set(unwrappedSelector, selectedKeySet)
                        publicSelectedKeysField.set(unwrappedSelector, selectedKeySet)

                        return null
                    } catch {
                        case e: NoSuchFieldException   => e
                        case e: IllegalAccessException => e
                    }
                })

                maybeException match {
                    case e: Exception =>
                        selectedKeys = null
                        logger.trace(s"failed to instrument a special java.util.Set into: ${unwrappedSelector} $e")
                        return SelectorTuple(unwrappedSelector)
                    case _ =>
                }
                selectedKeys = selectedKeySet
                logger.trace("")
                SelectorTuple(unwrappedSelector, new SelectedSelectionKeySetSelector(unwrappedSelector, selectedKeySet))
            }
        }
    }

    /** Replaces the current [[Selector]] of this event loop with newly created [[Selector]]s to work around the
     *  infamous epoll 100% CPU bug.
     */
    def rebuildSelector(): Unit = ???

    override def run(context: IoExecutionContext): Int = ???

    override def prepareToDestroy(): Unit = ???

    override def destroy(): Unit = try { selector.close() }
    catch {
        case e: IOException =>
            logger.warn(s"Failed to close a selector. ${ThrowableUtil.stackTraceToString(e)}")
    }

    override def register(channel: Channel): Unit = {
        val nioProcessor      = nioHandle(channel)
        var selected: Boolean = false
        var success: Boolean  = false
        while (!success) {
            try {
                nioProcessor.registerSelector(unwrappedSelector)
                channel.executorAddress.inform(RegisterReplyEvent(channel))
                success = true
            } catch {
                case e: CancelledKeyException =>
                    if (!selected) {
                        selectNow()
                        selected = true
                    } else channel.executorAddress.inform(RegisterReplyEvent(channel, succeed = false, cause = e))
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

    override def wakeup(inEventLoop: Boolean): Unit = ???

    override def isCompatible(handleType: Class[_ <: Channel]): Boolean =
        classOf[AbstractNioChannel[?, ?]].isAssignableFrom(handleType)

    @throws[IOException]
    private def selectNow(): Int = try { selector.selectNow() }
    finally if (wakenUp.get()) selector.wakeup()

    private def selectAgain(): Unit = try {
        needsToSelectAgain = false
        selector.selectNow()
    } catch {
        case t: Throwable =>
            logger.warn(s"Failed to update SelectionKeys. ${ThrowableUtil.stackTraceToString(t)}")
    }

}

object NioHandler {

    def newFactory(): IoHandlerFactory = new IoHandlerFactory {
        override def newHandler: IoHandler = new NioHandler()
    }

    def newFactory(selectorProvider: SelectorProvider, selectStrategyFactory: SelectStrategyFactory): IoHandlerFactory =
        new IoHandlerFactory {
            override def newHandler: IoHandler =
                new NioHandler(selectorProvider, selectStrategyFactory.newSelectStrategy())
        }

    final protected case class SelectorTuple(unwrappedSelector: Selector, selector: Selector) {
        def this(unwrappedSelector: Selector) = this(unwrappedSelector, unwrappedSelector)
    }

    object SelectorTuple {
        def apply(unwrappedSelector: Selector): SelectorTuple = SelectorTuple(unwrappedSelector, unwrappedSelector)
    }

    private val CLEANUP_INTERVAL = 256 // XXX Hard-coded value, but won't need customization.+

    private val DISABLE_KEY_SET_OPTIMIZATION =
        SystemPropertyUtil.getBoolean("io.netty5.noKeySetOptimization", false)

    private val MIN_PREMATURE_SELECTOR_RETURNS  = 3
    private var SELECTOR_AUTO_REBUILD_THRESHOLD = 0

    var selectorAutoRebuildThreshold: Int =
        SystemPropertyUtil.getInt("io.netty5.selectorAutoRebuildThreshold", 512)
    if (selectorAutoRebuildThreshold < MIN_PREMATURE_SELECTOR_RETURNS) selectorAutoRebuildThreshold = 0

    SELECTOR_AUTO_REBUILD_THRESHOLD = selectorAutoRebuildThreshold

    protected def nioHandle(handle: Channel): NioProcessor = handle match
        case channel: AbstractNioChannel[?, ?] => channel.nioProcessor
        case _ =>
            throw new IllegalArgumentException(s"Channel of type ${StringUtil.simpleClassName(handle)} not supported")

}
