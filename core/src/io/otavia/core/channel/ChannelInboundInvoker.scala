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

trait ChannelInboundInvoker {

    def fireChannelRegistered(): this.type

    def fireChannelUnregistered(): this.type

    def fireChannelActive(): this.type

    def fireChannelInactive(): this.type

    def fireChannelShutdown(direction: ChannelShutdownDirection): this.type

    def fireChannelExceptionCaught(cause: Throwable): this.type

    def fireChannelInboundEvent(event: AnyRef): this.type

    def fireChannelTimeoutEvent(id: Long): this.type

    def fireChannelRead(msg: AnyRef): this.type

    def fireChannelRead(msg: AnyRef, msgId: Long): this.type

    def fireChannelReadComplete(): this.type

    def fireChannelWritabilityChanged(): this.type

}
