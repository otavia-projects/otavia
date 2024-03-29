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

package cc.otavia.core.transport.nio

import cc.otavia.core.system.ActorSystem
import cc.otavia.core.transport.TransportFactory
import cc.otavia.core.transport.spi.TransportServiceProvider

import java.util.concurrent.atomic.AtomicBoolean
import scala.language.unsafeNulls

class NIOTransportServiceProvider extends TransportServiceProvider {

    private var transportFactory: TransportFactory = _

    override def getTransportFactory(): TransportFactory = transportFactory

    override def initialize(system: ActorSystem): Unit = {
        this.synchronized {
            if (transportFactory == null) transportFactory = new NIOTransportFactory(system)
        }
    }

    override def checkPlatformSupport(): Boolean = true

}
