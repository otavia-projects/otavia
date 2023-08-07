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

package cc.otavia.core.slf4a.spi

import cc.otavia.core.slf4a.{ILoggerFactory, LoggerFactory}

trait SLF4AServiceProvider {

    /** Return the instance of [[ILoggerFactory]] that [[LoggerFactory]] class should bind to.
     *
     *  @return
     *    instance of [[ILoggerFactory]]
     */
    def getLoggerFactory: ILoggerFactory

    /** Return the maximum API version for SLF4J that the logging implementation supports.
     *
     *  For example: "2.0.1".
     *
     *  @return
     *    the string API version.
     */
    def getRequestedApiVersion: String

    /** Initialize the logging back-end.
     *
     *  <p><b>WARNING:</b> This method is intended to be called once by [[LoggerFactory]] class and from nowhere else.
     */
    def initialize(): Unit

}
