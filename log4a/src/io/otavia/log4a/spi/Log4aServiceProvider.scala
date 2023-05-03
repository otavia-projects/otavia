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

package io.otavia.log4a.spi

import io.otavia.core.slf4a.ILoggerFactory
import io.otavia.core.slf4a.spi.SLF4AServiceProvider
import io.otavia.log4a.Log4aLoggerFactory

class Log4aServiceProvider extends SLF4AServiceProvider {

    private var loggerFactory: Log4aLoggerFactory = _

    override def getLoggerFactory: ILoggerFactory = loggerFactory

    override def getRequestedApiVersion: String = "1.0.0"

    override def initialize(): Unit = {
        loggerFactory = new Log4aLoggerFactory()
    }

}
