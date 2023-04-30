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

import io.otavia.core.slf4a.LogLevel.*
import io.otavia.core.system.ActorSystem
import io.otavia.core.util.ThrowableUtil

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

    /** Log an exception (throwable) at the TRACE level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    final def trace(msg: String, e: Throwable): Unit = trace(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

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

    /** Log an exception (throwable) at the [[DEBUG]] level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    final def debug(msg: String, e: Throwable): Unit = debug(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

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

    /** Log an exception (throwable) at the [[INFO]] level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    final def info(msg: String, e: Throwable): Unit = info(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

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

    /** Log an exception (throwable) at the [[WARN]] level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    final def warn(msg: String, e: Throwable): Unit = warn(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

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

    /** Log an exception (throwable) at the ERROR level with an accompanying message.
     *  @param msg
     *    the message accompanying the exception
     *  @param e
     *    the exception (throwable) to log
     */
    final def error(msg: String, e: Throwable): Unit = error(s"$msg\n${ThrowableUtil.stackTraceToString(e)}")

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
