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

package cc.otavia.handler.codec.string

import cc.otavia.common.{Platform, SystemPropertyUtil}

/** A class to represent line separators in different environments. */
class LineSeparator(val value: String) {

    override def hashCode(): Int = value.hashCode

}

object LineSeparator {

    /** The default line separator in the current system. */
    val DEFAULT = new LineSeparator(SystemPropertyUtil.get("line.separator", "\n"))

    /** The Unix line separator(LF) */
    val UNIX = new LineSeparator("\n")

    /** The Windows line separator(CRLF) */
    val WINDOWS = new LineSeparator("\r\n")

    /** The MacOS line separator(CR) */
    val MACOS = new LineSeparator("\r")

}
