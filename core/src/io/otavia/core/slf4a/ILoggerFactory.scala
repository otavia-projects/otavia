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

/** [[ILoggerFactory]] instances manufacture [[Logger]] instances by name. Most users retrieve [[Logger]] instances
 *  through the [[LoggerFactory.getLogger(String, ActorSystem)]] method. An instance of of this interface is bound
 *  internally with [[LoggerFactory]] class at compile time.
 */
trait ILoggerFactory {

    /** Return an appropriate [[Logger]] instance as specified by the name parameter. If the name parameter is equal to
     *  [[Logger.ROOT_LOGGER_NAME]], that is the string value "ROOT" (case insensitive), then the root logger of the
     *  underlying logging system is returned. Null-valued name arguments are considered invalid. Certain extremely
     *  simple logging systems, e.g. NOP, may always return the same logger instance regardless of the requested name.
     *
     *  @param name
     *    the name of the [[Logger]] to return
     *  @param system
     *    [[ActorSystem]] of the [[Logger]] running
     *  @return
     *    a [[Logger]] instance
     */
    def getLogger(name: String, system: ActorSystem): Logger = ???

}
