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

package io.otavia.core.slf4a

import io.otavia.core.ioc.ModuleListener
import io.otavia.core.slf4a.helpers.MessageFormatter
import io.otavia.core.util.ThrowableUtil

abstract class AbstractLogger extends Logger, Serializable, ModuleListener {

    override def trace(format: String, arg: Any): Unit = ???
//        if (isTraceEnabled) trace(MessageFormatter.format(format, arg))

    override def trace(format: String, arg1: Any, arg2: Any): Unit = ???

    override def trace(format: String, args: Any*): Unit = ???

    override def trace(msg: String, e: Throwable): Unit = ???
//        if (isTraceEnabled) trace(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

    override def debug(format: String, arg: Any): Unit = ???

    override def debug(format: String, arg1: Any, arg2: Any): Unit = ???

    override def debug(format: String, args: Any*): Unit = ???

    override def debug(msg: String, e: Throwable): Unit = ???
//        if (isDebugEnabled) trace(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

    override def isInfoEnabled: Boolean = ???

    override def info(format: String, arg: Any): Unit = ???

    override def info(format: String, arg1: Any, arg2: Any): Unit = ???

    override def info(format: String, args: Any*): Unit = ???

    override def info(msg: String, e: Throwable): Unit = ???
//        if (isInfoEnabled) trace(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

    override def warn(format: String, arg: Any): Unit = ???

    override def warn(format: String, arg1: Any, arg2: Any): Unit = ???

    override def warn(format: String, args: Any*): Unit = ???

    override def warn(msg: String, e: Throwable): Unit = ???
//        if (isWarnEnabled) trace(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

    override def error(format: String, arg: Any): Unit = ???

    override def error(format: String, arg1: Any, arg2: Any): Unit = ???

    override def error(format: String, args: Any*): Unit = ???

    override def error(msg: String, e: Throwable): Unit = ???
//        if (isErrorEnabled) trace(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

}
