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

import io.otavia.core.system.ActorSystem

object LoggerFactory {

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
    def getLogger(clz: Class[?], system: ActorSystem): Logger = ???

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
    def getLogger(name: String, system: ActorSystem): Logger = ???

}
