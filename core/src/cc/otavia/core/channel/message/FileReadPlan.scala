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

package cc.otavia.core.channel.message

import java.nio.channels.FileChannel

/** Read file channel from [[position]] with [[length]]， See also [[FileChannel]].
 *
 *  @param position
 *    If position is -1, bytes are read starting at this channel's current file position, and then the file position is
 *    updated with the number of bytes actually read. If [[position]] is not -1, then read starting at the given file
 *    position rather than at the channel's current position. This method does not modify this channel's position. If
 *    the given position is greater than the file's current size then no bytes are read.
 *
 *  @param length
 *    Length of bytes want to read. If length is -1, read until file end.
 */
case class FileReadPlan(length: Int = 4096, position: Long = -1) extends ReadPlan {

    override def estimatedNextSize: Int = 0

    override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = false

    override def readComplete(): Unit = {}

}
