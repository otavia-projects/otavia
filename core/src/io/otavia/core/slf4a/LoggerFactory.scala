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

import io.otavia.core.slf4a.helpers.Util
import io.otavia.core.slf4a.nop.NOPServiceProvider
import io.otavia.core.slf4a.spi.SLF4AServiceProvider
import io.otavia.core.system.ActorSystem

import java.security.{AccessController, PrivilegedAction}
import java.util
import java.util.{Iterator, ServiceConfigurationError, ServiceLoader}
import scala.collection.mutable
import scala.language.{existentials, unsafeNulls}

/** The [[LoggerFactory]] is a utility class producing Loggers for various logging APIs, most notably for slf4a-simple,
 *  and others. [[LoggerFactory]] is essentially a wrapper around an [[ILoggerFactory]] instance bound with
 *  [[LoggerFactory]] at compile time.
 */
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
    def getLogger(clz: Class[?], system: ActorSystem): Logger = {
        val logger = getLogger(clz.getName, system)
        logger
    }

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
    def getLogger(name: String, system: ActorSystem): Logger = {
        val iLoggerFactory = getILoggerFactory
        iLoggerFactory.getLogger(name, system)
    }

    private[slf4a] val UNINITIALIZED               = 0
    private[slf4a] val ONGOING_INITIALIZATION      = 1
    private[slf4a] val FAILED_INITIALIZATION       = 2
    private[slf4a] val SUCCESSFUL_INITIALIZATION   = 3
    private[slf4a] val NOP_FALLBACK_INITIALIZATION = 4

    @volatile private[slf4a] var INITIALIZATION_STATE = UNINITIALIZED

    @volatile private var PROVIDER: SLF4AServiceProvider = _

    private val NOP_SERVICE_PROVIDER = new NOPServiceProvider()

    private def getILoggerFactory: ILoggerFactory = getProvider.getLoggerFactory

    private def getProvider: SLF4AServiceProvider = this.synchronized {
        if (INITIALIZATION_STATE == UNINITIALIZED) {
            INITIALIZATION_STATE = ONGOING_INITIALIZATION
            performInitialization()
        }
        INITIALIZATION_STATE match
            case SUCCESSFUL_INITIALIZATION   => PROVIDER
            case NOP_FALLBACK_INITIALIZATION => NOP_SERVICE_PROVIDER
            case FAILED_INITIALIZATION       => throw new IllegalStateException()
    }

    private[slf4a] def reset(): Unit = INITIALIZATION_STATE = UNINITIALIZED

    private final def performInitialization(): Unit = {
        bind()
    }

    private final def bind(): Unit = try {
        val providersList = findServiceProviders()
        reportMultipleBindingAmbiguity(providersList)
        if (providersList.nonEmpty) {
            PROVIDER = providersList.head
            PROVIDER.initialize()
            INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION
            reportActualBinding(providersList)
        } else {
            INITIALIZATION_STATE = NOP_FALLBACK_INITIALIZATION
            Util.report("No SLF4A providers were found.")
            Util.report("Defaulting to no-operation (NOP) logger implementation")
        }
    } catch {
        case e: Exception =>
            failedBinding(e)
            throw new IllegalStateException("Unexpected initialization failure", e)
    }

    private def findServiceProviders(): mutable.ArrayBuffer[SLF4AServiceProvider] = {
        val classLoaderOfLoggerFactory = getClass.getClassLoader
        val serviceLoader              = getServiceLoader(classLoaderOfLoggerFactory)
        val providerList               = new mutable.ArrayBuffer[SLF4AServiceProvider]()
        val iterator                   = serviceLoader.iterator()
        while (iterator.hasNext) {
            safelyInstantiate(providerList, iterator)
        }
        providerList
    }

    private def getServiceLoader(classLoaderOfLoggerFactory: ClassLoader): ServiceLoader[SLF4AServiceProvider] = {
        val securityManager = System.getSecurityManager
        val serviceLoader = if (securityManager == null) {
            ServiceLoader.load(classOf[SLF4AServiceProvider], classLoaderOfLoggerFactory)
        } else {
            val action: PrivilegedAction[ServiceLoader[SLF4AServiceProvider]] = () =>
                ServiceLoader.load(classOf[SLF4AServiceProvider], classLoaderOfLoggerFactory)
            AccessController.doPrivileged[ServiceLoader[SLF4AServiceProvider]](action)
        }
        serviceLoader
    }

    private def safelyInstantiate(
        providerList: mutable.ArrayBuffer[SLF4AServiceProvider],
        iterator: util.Iterator[SLF4AServiceProvider]
    ): Unit = try {
        val provider = iterator.next()
        providerList.addOne(provider)
    } catch {
        case e: ServiceConfigurationError =>
            Util.report(s"A SLF4A service provider failed to instantiate:\n${e.getMessage}")
    }

    private def isAmbiguousProviderList(providerList: mutable.ArrayBuffer[SLF4AServiceProvider]): Boolean =
        providerList.length > 1

    private def reportMultipleBindingAmbiguity(providerList: mutable.ArrayBuffer[SLF4AServiceProvider]): Unit = {
        if (isAmbiguousProviderList(providerList)) {
            Util.report("Class path contains multiple SLF4A providers.")
            for (provider <- providerList) {
                Util.report(s"Found provider [${provider}]")
            }
            //
        }
    }

    private def reportActualBinding(providerList: mutable.ArrayBuffer[SLF4AServiceProvider]): Unit = {
        if (providerList.nonEmpty && isAmbiguousProviderList(providerList)) {
            Util.report(s"Actual provider is of type [${providerList.head}]")
        }
    }

    private def failedBinding(t: Throwable): Unit = {
        INITIALIZATION_STATE = FAILED_INITIALIZATION
        Util.report("Failed to instantiate SLF4A LoggerFactory", t)
    }

}
