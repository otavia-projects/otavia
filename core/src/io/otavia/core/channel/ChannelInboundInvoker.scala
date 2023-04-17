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

import io.otavia.core.reactor.Reactor

trait ChannelInboundInvoker {

    /** A [[Channel]] was registered to [[Reactor]]. This will result in having the
     *  [[ChannelHandler.channelRegistered(ChannelHandlerContext)]] method called of the next [[ChannelHandler]]
     *  contained in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelRegistered(): this.type

    /** A [[Channel]] was unregistered from [[Reactor]]. This will result in having the
     *  [[ChannelHandler.channelUnregistered(ChannelHandlerContext)]] method called of the next [[ChannelHandler]]
     *  contained in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelUnregistered(): this.type

    /** A [[Channel]] is active now, which means it is connected or opened. This will result in having the
     *  [[ChannelHandler.channelActive(ChannelHandlerContext)]] method called of the next [[ChannelHandler]] contained
     *  in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelActive(): this.type

    /** A [[Channel]] is inactive now, which means it is closed. This will result in having the
     *  [[ChannelHandler.channelInactive(ChannelHandlerContext)]] method called of the next [[ChannelHandler]] contained
     *  in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelInactive(): this.type

    /** A [[Channel]] was shutdown in a specific direction. This will result in having the
     *  [[ChannelHandler.channelShutdown(ChannelHandlerContext, ChannelShutdownDirection)]] method called of the next
     *  [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *  @param direction
     *    the direction of shutdown
     */
    def fireChannelShutdown(direction: ChannelShutdownDirection): this.type

    /** A [[Channel]] received an [[Throwable]] in one of its inbound operations. This will result in having the
     *  [[ChannelHandler.channelExceptionCaught(ChannelHandlerContext, Throwable)]] method called of the next
     *  [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelExceptionCaught(cause: Throwable): this.type

    /** A [[Channel]] received a custom defined inbound event. This will result in having the
     *  [[ChannelHandler.channelInboundEvent(ChannelHandlerContext, Object)]] method called of the next
     *  [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelInboundEvent(event: AnyRef): this.type

    /** A [[Channel]] received a custom defined timeout event. This will result in having the
     *  [[ChannelHandler.channelTimeoutEvent]] method called of the next [[ChannelHandler]] contained in the
     *  [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param id
     *    timer task register id
     */
    def fireChannelTimeoutEvent(id: Long): this.type

    /** A [[Channel]] received a message. This will result in having the
     *  [[ChannelHandler.channelRead(ChannelHandlerContext, Object)]] method called of the next [[ChannelHandler]]
     *  contained in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelRead(msg: AnyRef): this.type

    /** A [[Channel]] received a message. This will result in having the
     *  [[ChannelHandler.channelRead(ChannelHandlerContext, Object)]] method called of the next [[ChannelHandler]]
     *  contained in the [[ChannelPipeline]] of the [[Channel]].
     */
    def fireChannelRead(msg: AnyRef, msgId: Long): this.type

    /** Triggers an [[ChannelHandler.channelReadComplete(ChannelHandlerContext)]] event to the next [[ChannelHandler]]
     *  in the [[ChannelPipeline]].
     */
    def fireChannelReadComplete(): this.type

    /** Triggers an [[ChannelHandler.channelWritabilityChanged(ChannelHandlerContext)]] event to the next
     *  [[ChannelHandler]] in the [[ChannelPipeline]].
     */
    def fireChannelWritabilityChanged(): this.type

}
