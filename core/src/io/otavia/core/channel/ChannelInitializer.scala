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

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.{Channel, ChannelHandler}

/** A special [[ChannelHandler]] which offers an easy way to initialize a [[Channel]] once it was registered to its
 *  [[ChannelsActor]]. Implementations are most often used in the context of [[ChannelsActor.handler]], to setup the
 *  [[ChannelPipeline]] of a [[Channel]].
 *
 *  {{{
 *      class MyChannelInitializer extends ChannelInitializer[Channel] {
 *          override protected def initChannel(ch: Channel): Unit = {
 *              ch.pipeline.addLast("myHandler", new MyHandler())
 *          }
 *      }
 *  }}}
 *  Be aware that this class is marked as [[isSharable]] and so the implementation must be safe to be re-used
 *  @tparam C
 *    A sub-type of [[Channel]]
 */
abstract class ChannelInitializer[C <: Channel] extends ChannelHandler {

    // TODO: inject actor logger, and reuse this object.
    override def isSharable: Boolean = true

    /** This method will be called once the [[Channel]] was registered. After the method returns this instance will be
     *  removed from the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param ch
     *    the [[Channel]] which was registered.
     *  @throws Exception
     *    is thrown if an error occurs. In that case it will be handled by [[channelExceptionCaught]] which will by
     *    default close the [[Channel]].
     */
    @throws[Exception]
    protected def initChannel(ch: C): Unit

    /** Handle the [[Throwable]] by logging and closing the [[Channel]]. Sub-classes may override this. */
    override def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
        // TODO: logger.warn
        ctx.close()
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        try {
            initChannel(ctx.channel.asInstanceOf[C])
        } catch {
            // Explicitly call exceptionCaught(...) as we removed the handler before calling initChannel(...).
            // We do so to prevent multiple calls to initChannel(...).
            case cause: Throwable => channelExceptionCaught(ctx, cause)
        } finally {
            if (!ctx.isRemoved) ctx.pipeline.remove(this)
        }
    }

}
