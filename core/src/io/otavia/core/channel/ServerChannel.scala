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

/** A [[Channel]] that accepts an incoming connection attempts and creates its child [[Channel]]s by accepting them. A
 *  [[ServerChannel]] does not allow the following operations and so will throws [[Exception]] of these operations:
 *
 *    1. connect(SocketAddress)
 *    1. disconnect()
 *    1. write(Object)
 *    1. flush()
 *    1. shutdown(ChannelShutdownDirection)
 */
trait ServerChannel extends Channel
