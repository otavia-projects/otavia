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

package io.otavia.core.channel

import io.otavia.core.buffer.AdaptiveBuffer
import io.otavia.core.channel.*
import io.otavia.core.channel.estimator.ReadBufferAllocator

trait ChannelPipeline extends ChannelInboundInvoker with ChannelOutboundInvoker {

    /** Inserts a [[ChannelHandler]] at the first position of this pipeline.
     *
     *  @param name
     *    the name of the handler to insert first
     *  @param handler
     *    the handler to insert first
     *  @return
     *    itself.
     *  @throws IllegalArgumentException
     *    if there's an entry with the same name already in the pipeline
     *  @throws NullPointerException
     *    if the specified handler is `null`
     */
    def addFirst(name: Option[String], handler: ChannelHandler): ChannelPipeline

    /** Inserts a [[ChannelHandler]] at the first position of this pipeline.
     *
     *  @param handler
     *    the handler to insert first
     *  @return
     *    itself.
     *  @throws IllegalArgumentException
     *    if there's an entry with the same name already in the pipeline
     *  @throws NullPointerException
     *    if the specified handler is `null`
     */
    def addFirst(handler: ChannelHandler): ChannelPipeline = this.addFirst(None, handler)

    /** Appends a [[ChannelHandler]] at the last position of this pipeline.
     *
     *  @param name
     *    the name of the handler to append
     *  @param handler
     *    the handler to append
     *  @throws IllegalArgumentException
     *    if there's an entry with the same name already in the pipeline
     *  @throws NullPointerException
     *    if the specified handler is `null`
     */
    def addLast(name: Option[String], handler: ChannelHandler): ChannelPipeline

    /** Appends a [[ChannelHandler]] at the last position of this pipeline.
     *
     *  @param handler
     *    the handler to append
     *  @throws IllegalArgumentException
     *    if there's an entry with the same name already in the pipeline
     *  @throws NullPointerException
     *    if the specified handler is `null`
     */
    def addLast(handler: ChannelHandler): ChannelPipeline = this.addLast(None, handler)

    /** Inserts a [[ChannelHandler]] before an existing handler of this pipeline.
     *
     *  @param baseName
     *    the name of the existing handler
     *  @param name
     *    the name of the handler to insert before
     *  @param handler
     *    the handler to insert before
     *  @throws NoSuchElementException
     *    if there's no such entry with the specified [[baseName]]
     *  @throws IllegalArgumentException
     *    if there's an entry with the same name already in the pipeline
     *  @throws NullPointerException
     *    if the specified baseName or handler is `null`
     */
    def addBefore(baseName: String, name: Option[String], handler: ChannelHandler): ChannelPipeline

    /** Inserts a [[ChannelHandler]] after an existing handler of this pipeline.
     *
     *  @param baseName
     *    the name of the existing handler
     *  @param name
     *    the name of the handler to insert after
     *  @param handler
     *    the handler to insert after
     *  @throws NoSuchElementException
     *    if there's no such entry with the specified [[baseName]]
     *  @throws IllegalArgumentException
     *    if there's an entry with the same name already in the pipeline
     *  @throws NullPointerException
     *    if the specified baseName or handler is `null`
     */
    def addAfter(baseName: String, name: Option[String], handler: ChannelHandler): ChannelPipeline

    /** Inserts [[ChannelHandler]]s at the first position of this pipeline. `null` handlers will be skipped.
     *
     *  @param handlers
     *    the handlers to insert first
     */
    def addFirst(handlers: ChannelHandler*): ChannelPipeline

    /** Inserts [[ChannelHandler]]s at the last position of this pipeline. `null` handlers will be skipped.
     *
     *  @param handlers
     *    the handlers to insert last
     */
    def addLast(handlers: ChannelHandler*): ChannelPipeline

    /** Removes the specified [[ChannelHandler]] from this pipeline.
     *
     *  @param handler
     *    the [[ChannelHandler]] to remove
     *  @throws NoSuchElementException
     *    if there's no such handler in this pipeline
     *  @throws NullPointerException
     *    if the specified handler is `null`
     */
    def remove(handler: ChannelHandler): ChannelPipeline

    /** Removes the [[ChannelHandler]] with the specified name from this pipeline.
     *
     *  @param name
     *    the name under which the [[ChannelHandler]] was stored.
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if there's no such handler with the specified name in this pipeline
     *  @throws NullPointerException
     *    if the specified name is `null`
     */
    def remove(name: String): Option[ChannelHandler]

    /** Removes the [[ChannelHandler]] of the specified type from this pipeline.
     *
     *  @tparam T
     *    the type of the handler
     *  @param handlerType
     *    the type of the handler
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if there's no such handler of the specified type in this pipeline
     *  @throws NullPointerException
     *    if the specified handler type is `null`
     */
    def remove[T <: ChannelHandler](handlerType: Class[T]): Option[T]

    /** Removes the [[ChannelHandler]] with the specified name from this pipeline if it exists.
     *
     *  @param name
     *    the name under which the [[ChannelHandler]] was stored.
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if there's no such handler with the specified name in this pipeline
     *  @throws NullPointerException
     *    if the specified name is `null`
     */
    def removeIfExists[T <: ChannelHandler](name: String): Option[T]

    /** Removes the [[ChannelHandler]] of the specified type from this pipeline if it exists.
     *
     *  @tparam T
     *    the type of the handler
     *  @param handlerType
     *    the type of the handler
     *  @return
     *    the removed handler or `null` if it didn't exist.
     *  @throws NullPointerException
     *    if the specified handler type is `null`
     */
    def removeIfExists[T <: ChannelHandler](handlerType: Class[T]): Option[T]

    /** Removes the specified [[ChannelHandler]] from this pipeline if it exists
     *
     *  @param handler
     *    the [[ChannelHandler]] to remove
     *  @return
     *    the removed handler or `null` if it didn't exist.
     *  @throws NullPointerException
     *    if the specified handler is `null`
     */
    def removeIfExists[T <: ChannelHandler](handler: ChannelHandler): Option[T]

    /** Removes the first [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the removed handler, None if this pipeline is empty
     */
    def removeFirst(): Option[ChannelHandler]

    /** Removes the last [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the removed handler, None if this pipeline is empty
     */
    def removeLast(): Option[ChannelHandler]

    /** Replaces the specified [[ChannelHandler]] with a new handler in this pipeline.
     *
     *  @param oldHandler
     *    the [[ChannelHandler]] to be replaced
     *  @param newName
     *    the name under which the replacement should be added
     *  @param newHandler
     *    the [[ChannelHandler]] which is used as replacement
     *  @return
     *    itself
     *  @throws NoSuchElementException
     *    if the specified old handler does not exist in this pipeline
     *  @throws IllegalArgumentException
     *    if a handler with the specified new name already exists in this pipeline, except for the handler to be
     *    replaced
     *  @throws NullPointerException
     *    if the specified old handler or new handler is `null`
     */
    def replace(oldHandler: ChannelHandler, newName: Option[String], newHandler: ChannelHandler): ChannelPipeline

    /** Replaces the [[ChannelHandler]] of the specified name with a new handler in this pipeline.
     *
     *  @param oldName
     *    the name of the [[ChannelHandler]] to be replaced
     *  @param newName
     *    the name under which the replacement should be added
     *  @param newHandler
     *    the [[ChannelHandler]] which is used as replacement
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if the handler with the specified old name does not exist in this pipeline
     *  @throws IllegalArgumentException
     *    if a handler with the specified new name already exists in this pipeline, except for the handler to be
     *    replaced
     *  @throws NullPointerException
     *    if the specified old handler or new handler is `null`
     */
    def replace(oldName: String, newName: Option[String], newHandler: ChannelHandler): ChannelHandler

    /** Replaces the [[ChannelHandler]] of the specified type with a new handler in this pipeline.
     *
     *  @param oldHandlerType
     *    the type of the handler to be removed
     *  @param newName
     *    the name under which the replacement should be added
     *  @param newHandler
     *    the [[ChannelHandler]] which is used as replacement
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if the handler of the specified old handler type does not exist in this pipeline
     *  @throws IllegalArgumentException
     *    if a handler with the specified new name already exists in this pipeline, except for the handler to be
     *    replaced
     *  @throws NullPointerException
     *    if the specified old handler or new handler is `null`
     */
    def replace[T <: ChannelHandler](oldHandlerType: Class[T], newName: Option[String], newHandler: ChannelHandler): T

    /** Returns the first [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the first handler. [[None]] if this pipeline is empty.
     */
    def first: Option[ChannelHandler] = firstContext match
        case Some(ctx) => Some(ctx.handler)
        case None      => None

    /** Returns the context of the first [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the context of the first handler. [[None]] if this pipeline is empty.
     */
    def firstContext: Option[ChannelHandlerContext]

    /** Returns the last [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the last handler. [[None]] if this pipeline is empty.
     */
    def last: Option[ChannelHandler] = lastContext match
        case Some(ctx) => Some(ctx.handler)
        case None      => None

    /** Returns the context of the last [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the context of the last handler. [[None]] if this pipeline is empty.
     */
    def lastContext: Option[ChannelHandlerContext]

    /** Returns true if this [[ChannelPipeline]] is empty, which means no [[ChannelHandler]] is present. */
    def isEmpty: Boolean = lastContext.isEmpty

    /** Returns the [[ChannelHandler]] with the specified name in this pipeline.
     *
     *  @return
     *    the handler with the specified name. [[None]] if there's no such handler in this pipeline.
     */
    def get(name: String): Option[ChannelHandler]

    /** Returns the [[ChannelHandler]] of the specified type in this pipeline.
     *
     *  @return
     *    the handler of the specified handler type. [[None]] if there's no such handler in this pipeline.
     */
    def get[T <: ChannelHandler](handlerType: Class[T]): Option[T]

    /** Returns the context object of the specified [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the context object of the specified handler. `null` if there's no such handler in this pipeline.
     */
    def context(handler: ChannelHandler): Option[ChannelHandlerContext]

    /** Returns the context object of the [[ChannelHandler]] with the specified name in this pipeline.
     *
     *  @return
     *    the context object of the handler with the specified name. `null` if there's no such handler in this pipeline.
     */
    def context(name: String): Option[ChannelHandlerContext]

    /** Returns the context object of the [[ChannelHandler]] of the specified type in this pipeline.
     *
     *  @return
     *    the context object of the handler of the specified type. `null` if there's no such handler in this pipeline.
     */
    def context(handlerType: Class[? <: ChannelHandler]): Option[ChannelHandlerContext]

    /** Returns the [[Channel]] that this pipeline is attached to.
     *
     *  @return
     *    the channel.
     */
    def channel: Channel

    /** Returns the [[Iterable]] of the handler names. */
    final def names: Iterable[String] = toMap.keys

    /** Converts this pipeline into an ordered [[Map]] whose keys are handler names and whose values are handlers. */
    def toMap: Map[String, ChannelHandler]

    /** The number of the outbound bytes that are buffered / queued in this [[ChannelPipeline]]. This number will affect
     *  the writability of the [[Channel]] together the buffered / queued bytes in the [[Channel]] itself.
     *
     *  @return
     *    the number of buffered / queued bytes.
     */
    def pendingOutboundBytes: Long

    final def assertInExecutor(): Unit =
        assert(executor.inExecutor(), "method must be called in ChannelsActor which this channel registered!")

    private[core] def channelInboundBuffer: AdaptiveBuffer

    private[core] def channelOutboundBuffer: AdaptiveBuffer

    private[core] def closeInboundAdaptiveBuffers(): Unit = ???

    private[core] def closeOutboundAdaptiveBuffers(): Unit = ???

}
