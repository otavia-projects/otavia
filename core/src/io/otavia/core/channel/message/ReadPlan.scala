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

package io.otavia.core.channel.message

import io.otavia.core.channel.ChannelOutboundInvoker

import java.nio.channels.FileChannel

/** [[ReadPlan]] is a trait usage by [[ChannelOutboundInvoker.read]] to describe how to read data read from a channel */
trait ReadPlan

/** Read file channel from [[position]] with [[length]]， See also [[FileChannel]].
 *
 *  @param position
 *    If position is [[None]], bytes are read starting at this channel's current file position, and then the file
 *    position is updated with the number of bytes actually read. If [[position]] is not [[None]], then set the file
 *    current position to [[position]] and then read starting at this channel's current file position, and then the file
 *    position is updated with the number of bytes actually read.
 *  @param length
 *    Length of data want to read.
 */
case class FileRead(length: Int = 4096, position: Option[Long] = None) extends ReadPlan
