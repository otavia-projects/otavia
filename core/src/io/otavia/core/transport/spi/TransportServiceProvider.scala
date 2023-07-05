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

package io.otavia.core.transport.spi

import io.otavia.core.system.ActorSystem
import io.otavia.core.transport.TransportFactory

trait TransportServiceProvider {

    /** Return the instance of [[TransportFactory]] that [[TransportFactory]] class should bind to.
     *
     *  @return
     *    instance of [[TransportFactory]]
     */
    def getTransportFactory(): TransportFactory

    /** Initialize the channel back-end.
     *
     *  <p><b>WARNING:</b> This method is intended to be called once by [[TransportFactory]] class and from nowhere
     *  else.
     */
    def initialize(system: ActorSystem): Unit

    /** Check the [[TransportServiceProvider]] whether support this platform.
     *
     *  @return
     *    True if support, otherwise false.
     */
    def checkPlatformSupport(): Boolean

}
