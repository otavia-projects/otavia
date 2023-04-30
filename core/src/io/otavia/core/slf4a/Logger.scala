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

trait Logger {

    def trace(log: String): Unit
    def trace(log: String, e: Throwable): Unit

    def debug(log: String): Unit
    def debug(log: String, e: Throwable): Unit

    def info(log: String): Unit
    def info(log: String, e: Throwable): Unit

    def warn(log: String): Unit
    def warn(log: String, e: Throwable): Unit

    def error(log: String): Unit
    def error(log: String, e: Throwable): Unit

    def fatal(log: String): Unit
    def fatal(log: String, e: Throwable): Unit

}

object Logger {

    def getLogger(clz: Class[?], system: ActorSystem): Logger = LoggerFactory.getLogger(clz, system)

}
