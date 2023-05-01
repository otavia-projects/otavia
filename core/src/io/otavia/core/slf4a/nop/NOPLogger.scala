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

package io.otavia.core.slf4a.nop

import io.otavia.core.slf4a.Logger

/** A direct NOP (no operation) implementation of [[Logger]]. */
object NOPLogger extends Logger {

    override def getName: String = "NOP"

    override def isTraceEnabled: Boolean = false

    override def trace(msg: String): Unit = {}

    override def isDebugEnabled: Boolean = false

    override def debug(msg: String): Unit = {}

    override def isInfoEnabled: Boolean = false

    override def info(msg: String): Unit = {}

    override def isWarnEnabled: Boolean = false

    override def warn(msg: String): Unit = {}

    override def isErrorEnabled: Boolean = false

    override def error(msg: String): Unit = {}

}
