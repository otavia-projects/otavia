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

package io.otavia.core.transport

import io.otavia.core.channel.Channel
import io.otavia.core.channel.socket.SocketProtocolFamily
import io.otavia.core.reactor.{IoHandler, Reactor}
import io.otavia.core.system.ActorSystem
import io.otavia.core.transport.nio.NIOTransportServiceProvider
import io.otavia.core.transport.spi.TransportServiceProvider
import io.otavia.core.util.Report

import java.net.ProtocolFamily
import java.security.{AccessController, PrivilegedAction}
import java.util.ServiceLoader
import scala.collection.mutable
import scala.language.unsafeNulls

abstract class TransportFactory() {

    def openServerSocketChannel(): Channel

    def openServerSocketChannel(family: ProtocolFamily): Channel

    def openSocketChannel(): Channel

    def openSocketChannel(family: ProtocolFamily): Channel

    def openDatagramChannel(): Channel

    def openDatagramChannel(family: SocketProtocolFamily): Channel

    def openFileChannel(): Channel

    def openReactor(system: ActorSystem): Reactor

    def openIoHandler(system: ActorSystem): IoHandler

}

object TransportFactory {

    private val UNINITIALIZED               = 0
    private val ONGOING_INITIALIZATION      = 1
    private val FAILED_INITIALIZATION       = 2
    private val SUCCESSFUL_INITIALIZATION   = 3
    private val NOP_FALLBACK_INITIALIZATION = 4

    @volatile private var INITIALIZATION_STATE               = UNINITIALIZED
    @volatile private var PROVIDER: TransportServiceProvider = _

    private val NIO_SERVICE_PROVIDER = new NIOTransportServiceProvider()

    def getTransportFactory(system: ActorSystem): TransportFactory = {
        val provider = getProvider(system)
        provider.getTransportFactory()
    }

    private def getProvider(system: ActorSystem): TransportServiceProvider = this.synchronized {
        if (INITIALIZATION_STATE == UNINITIALIZED) {
            INITIALIZATION_STATE = ONGOING_INITIALIZATION
            bind(system)
        }
        INITIALIZATION_STATE match
            case SUCCESSFUL_INITIALIZATION => PROVIDER
            case NOP_FALLBACK_INITIALIZATION =>
                NIO_SERVICE_PROVIDER.initialize(system)
                NIO_SERVICE_PROVIDER
            case FAILED_INITIALIZATION => throw new IllegalStateException()
    }

    final private def bind(system: ActorSystem): Unit = try {
        val providerList = findServiceProviders()
        if (providerList.length > 1) {
            Report.report("Class path contains multiple Transport providers.", "TRANSPORT")
            for (provider <- providerList) {
                Report.report(s"Found provider [${provider}].", "TRANSPORT")
            }
        }
        if (providerList.nonEmpty) {
            PROVIDER = providerList.head
            PROVIDER.initialize(system)
            INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION
            Report.report(s"Actual provider is of type [${providerList.head}]", "TRANSPORT")
        } else {
            INITIALIZATION_STATE = NOP_FALLBACK_INITIALIZATION
            Report.report("No Transport providers were found.", "TRANSPORT")
            Report.report("Defaulting to JDK NIO transport implementation.", "TRANSPORT")
        }
    } catch {
        case e: Exception =>
            INITIALIZATION_STATE = FAILED_INITIALIZATION
            Report.report("Failed to instantiate transport TransportFactory", e)
            throw new IllegalStateException("Unexpected initialization failure", e)
    }

    final private def findServiceProviders(): mutable.ArrayBuffer[TransportServiceProvider] = {
        val classLoaderOfLoggerFactory = getClass.getClassLoader
        val serviceLoader              = getServiceLoader(classLoaderOfLoggerFactory)
        val providerList               = new mutable.ArrayBuffer[TransportServiceProvider]()
        val iterator                   = serviceLoader.iterator()
        while (iterator.hasNext) {
            val provider = iterator.next()
            if (provider.checkPlatformSupport()) providerList.addOne(provider)
            else {
                Report.report(s"Transport ${provider} is not support current platform, ignored!", "TRANSPORT")
            }
        }
        providerList
    }

    private def getServiceLoader(classLoaderOfLoggerFactory: ClassLoader): ServiceLoader[TransportServiceProvider] = {
        val securityManager = System.getSecurityManager
        val serviceLoader = if (securityManager == null) {
            ServiceLoader.load(classOf[TransportServiceProvider], classLoaderOfLoggerFactory)
        } else {
            val action: PrivilegedAction[ServiceLoader[TransportServiceProvider]] = () =>
                ServiceLoader.load(classOf[TransportServiceProvider], classLoaderOfLoggerFactory)
            AccessController.doPrivileged[ServiceLoader[TransportServiceProvider]](action)
        }
        serviceLoader
    }

}
