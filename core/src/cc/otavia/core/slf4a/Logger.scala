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

package cc.otavia.core.slf4a

import cc.otavia.core.slf4a.LogLevel.*
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.util.ThrowableUtil

/** The [[Logger]] interface is the main user entry point of SLF4A API. It is expected that logging takes place through
 *  concrete implementations of this interface.
 *
 *  <h3>Example</h3>
 *  {{{
 *      @main def run(): Unit = {
 *          val system = ActorSystem()
 *          val logger = LoggerFactory.getLogger(getClass, system)
 *          logger.info("actor system started!")
 *      }
 *  }}}
 */
trait Logger {

    /** Return the name of this Logger instance.
     *  @return
     *    name of this logger instance
     */
    def getName: String

    /** Is the logger instance enabled for the [[TRACE]] level?
     *  @return
     *    True if this Logger is enabled for the [[TRACE]] level, false otherwise.
     */
    def isTraceEnabled: Boolean

    /** Log a message at the [[TRACE]] level.
     *  @param msg
     *    the message string to be logged
     */
    def trace(msg: String): Unit

    /** Log a message at the [[TRACE]] level according to the specified format and argument.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[TRACE]] level.
     *
     *  @param format
     *    the format string
     *  @param arg
     *    the argument
     */
    def trace(format: String, arg: Any): Unit

    /** Log a message at the [[TRACE]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[TRACE]] level.
     *
     *  @param format
     *    the format string
     *  @param arg1
     *    the first argument
     *  @param arg2
     *    the second argument
     */
    def trace(format: String, arg1: Any, arg2: Any): Unit

    /** Log a message at the [[TRACE]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous string concatenation when the logger is disabled for the [[TRACE]] level. However,
     *  this variant incurs the hidden (and relatively small) cost of creating an [[Seq]] before invoking the method,
     *  even if this logger is disabled for [[TRACE]]. The variants taking one and two arguments exist solely in order
     *  to avoid this hidden cost.
     *
     *  @param format
     *    the format string
     *  @param args
     *    a list of 3 or more arguments
     */
    def trace(format: String, args: Any*): Unit

    /** Log an exception (throwable) at the TRACE level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    def trace(msg: String, e: Throwable): Unit

    /** Is the logger instance enabled for the [[DEBUG]] level?
     *
     *  @return
     *    True if this Logger is enabled for the [[DEBUG]] level, false otherwise.
     */
    def isDebugEnabled: Boolean

    /** Log a message at the [[DEBUG]] level.
     *  @param msg
     *    the message string to be logged
     */
    def debug(msg: String): Unit

    /** Log a message at the [[DEBUG]] level according to the specified format and argument.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[DEBUG]] level.
     *
     *  @param format
     *    the format string
     *  @param arg
     *    the argument
     */
    def debug(format: String, arg: Any): Unit

    /** Log a message at the [[DEBUG]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[DEBUG]] level.
     *
     *  @param format
     *    the format string
     *  @param arg1
     *    the first argument
     *  @param arg2
     *    the second argument
     */
    def debug(format: String, arg1: Any, arg2: Any): Unit

    /** Log a message at the [[DEBUG]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous string concatenation when the logger is disabled for the [[DEBUG]] level. However,
     *  this variant incurs the hidden (and relatively small) cost of creating an [[Seq]] before invoking the method,
     *  even if this logger is disabled for [[DEBUG]]. The variants taking one and two arguments exist solely in order
     *  to avoid this hidden cost.
     *
     *  @param format
     *    the format string
     *  @param arguments
     *    a list of 3 or more arguments
     */
    def debug(format: String, args: Any*): Unit

    /** Log an exception (throwable) at the [[DEBUG]] level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    def debug(msg: String, e: Throwable): Unit

    /** Is the logger instance enabled for the [[INFO]] level?
     *  @return
     *    True if this Logger is enabled for the [[INFO]] level, false otherwise.
     */
    def isInfoEnabled: Boolean

    /** Log a message at the [[INFO]] level.
     *  @param msg
     *    the message string to be logged
     */
    def info(msg: String): Unit

    /** Log a message at the [[INFO]] level according to the specified format and argument.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[INFO]] level.
     *
     *  @param format
     *    the format string
     *  @param arg
     *    the argument
     */
    def info(format: String, arg: Any): Unit

    /** Log a message at the [[INFO]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[INFO]] level.
     *
     *  @param format
     *    the format string
     *  @param arg1
     *    the first argument
     *  @param arg2
     *    the second argument
     */
    def info(format: String, arg1: Any, arg2: Any): Unit

    /** Log a message at the [[INFO]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous string concatenation when the logger is disabled for the [[INFO]] level. However,
     *  this variant incurs the hidden (and relatively small) cost of creating an [[Seq]] before invoking the method,
     *  even if this logger is disabled for [[INFO]]. The variants taking one and two arguments exist solely in order to
     *  avoid this hidden cost.
     *
     *  @param format
     *    the format string
     *  @param arguments
     *    a list of 3 or more arguments
     */
    def info(format: String, args: Any*): Unit

    /** Log an exception (throwable) at the [[INFO]] level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    def info(msg: String, e: Throwable): Unit

    /** Is the logger instance enabled for the [[WARN]] level?
     *  @return
     *    True if this Logger is enabled for the [[WARN]] level, false otherwise.
     */
    def isWarnEnabled: Boolean

    /** Log a message at the [[WARN]] level.
     *  @param msg
     *    the message string to be logged
     */
    def warn(msg: String): Unit

    /** Log a message at the [[WARN]] level according to the specified format and argument.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[WARN]] level.
     *
     *  @param format
     *    the format string
     *  @param arg
     *    the argument
     */
    def warn(format: String, arg: Any): Unit

    /** Log a message at the [[WARN]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[WARN]] level.
     *
     *  @param format
     *    the format string
     *  @param arg1
     *    the first argument
     *  @param arg2
     *    the second argument
     */
    def warn(format: String, arg1: Any, arg2: Any): Unit

    /** Log a message at the [[WARN]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous string concatenation when the logger is disabled for the [[WARN]] level. However,
     *  this variant incurs the hidden (and relatively small) cost of creating an [[Seq]] before invoking the method,
     *  even if this logger is disabled for [[WARN]]. The variants taking one and two arguments exist solely in order to
     *  avoid this hidden cost.
     *
     *  @param format
     *    the format string
     *  @param arguments
     *    a list of 3 or more arguments
     */
    def warn(format: String, args: Any*): Unit

    /** Log an exception (throwable) at the [[WARN]] level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    def warn(msg: String, e: Throwable): Unit

    /** Is the logger instance enabled for the [[ERROR]] level?
     *  @return
     *    True if this [[Logger]] is enabled for the [[ERROR]] level, false otherwise.
     */
    def isErrorEnabled: Boolean

    /** Log a message at the [[ERROR]] level.
     *  @param msg
     *    the message string to be logged
     */
    def error(msg: String): Unit

    /** Log a message at the [[ERROR]] level according to the specified format and argument.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[ERROR]] level.
     *
     *  @param format
     *    the format string
     *  @param arg
     *    the argument
     */
    def error(format: String, arg: Any): Unit

    /** Log a message at the [[ERROR]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous object creation when the logger is disabled for the [[ERROR]] level.
     *
     *  @param format
     *    the format string
     *  @param arg1
     *    the first argument
     *  @param arg2
     *    the second argument
     */
    def error(format: String, arg1: Any, arg2: Any): Unit

    /** Log a message at the [[ERROR]] level according to the specified format and arguments.
     *
     *  This form avoids superfluous string concatenation when the logger is disabled for the [[ERROR]] level. However,
     *  this variant incurs the hidden (and relatively small) cost of creating an [[Seq]] before invoking the method,
     *  even if this logger is disabled for [[ERROR]]. The variants taking one and two arguments exist solely in order
     *  to avoid this hidden cost.
     *
     *  @param format
     *    the format string
     *  @param arguments
     *    a list of 3 or more arguments
     */
    def error(format: String, args: Any*): Unit

    /** Log an exception (throwable) at the ERROR level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    def error(msg: String, e: Throwable): Unit

}

object Logger {

    /** Case insensitive String constant used to retrieve the name of the root logger. */
    val ROOT_LOGGER_NAME: String = "ROOT"

    /** Return a logger named corresponding to the class passed as parameter, using the statically bound ILoggerFactory
     *  instance. In case the the clazz parameter differs from the name of the caller as computed internally by SLF4J, a
     *  logger name mismatch warning will be printed but only if the slf4j.detectLoggerNameMismatch system property is
     *  set to true. By default, this property is not set and no warnings will be printed even in case of a logger name
     *  mismatch.
     *
     *  @param clz
     *    the returned logger will be named after clazz
     *  @param system
     *    [[ActorSystem]] of the [[Logger]] running
     *  @return
     *    a [[Logger]] instance
     */
    def getLogger(clz: Class[?], system: ActorSystem): Logger = LoggerFactory.getLogger(clz, system)

    /** Return a [[logger]] named according to the name parameter using the statically bound [[ILoggerFactory]]
     *  instance.
     *
     *  @param name
     *    The name of the logger.
     *  @param system
     *    [[ActorSystem]] of the [[Logger]] running
     *  @return
     *    a [[Logger]] instance
     */
    def getLogger(name: String, system: ActorSystem): Logger = LoggerFactory.getLogger(name, system)

}
