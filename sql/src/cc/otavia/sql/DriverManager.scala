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

package cc.otavia.sql

import cc.otavia.common.Report
import cc.otavia.sql.datatype.Money
import cc.otavia.sql.spi.ADBCServiceProvider
import cc.otavia.core.slf4a.spi.SLF4AServiceProvider

import java.util
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import java.util.{ServiceConfigurationError, ServiceLoader}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.language.unsafeNulls

object DriverManager {

    private val DEFAULT_DRIVER =
        Map("mysql" -> "cc.otavia.mysql.MySQLDriver", "postgres" -> "cc.otavia.postgres.PostgresDriver")

    private val drivers: ConcurrentHashMap[String, DriverFactory] = new ConcurrentHashMap[String, DriverFactory]()

    def defaultDriver(url: String): DriverFactory =
        if (url.trim.toLowerCase.startsWith("jdbc:mysql:")) getDriverFactory(DEFAULT_DRIVER("mysql"))
        else if (url.trim.toLowerCase.startsWith("jdbc:postgresql:"))
            getDriverFactory(DEFAULT_DRIVER("postgres"))
        else throw new IllegalArgumentException(s"Schema not supported yet: ${url}")

    def getDriverFactory(driverName: String): DriverFactory = {
        if (drivers.isEmpty) findServiceProviders()
        if (drivers.containsKey(driverName))
            drivers.get(driverName)
        else
            throw new IllegalArgumentException(
              s"Can't find ADBC driver for name ${driverName}, current supported drivers are ${drivers.keys().asScala.mkString("[", ",", "]")}"
            )
    }

    private def findServiceProviders(): Unit = {
        val classLoaderOfLoggerFactory = getClass.getClassLoader
        val serviceLoader              = getServiceLoader(classLoaderOfLoggerFactory)
        val providerList               = new mutable.ArrayBuffer[ADBCServiceProvider]()
        val iterator                   = serviceLoader.iterator()
        while (iterator.hasNext) safelyInstantiate(providerList, iterator)
        for (provider <- providerList) {
            val factory = provider.getDriverFactory
            drivers.put(factory.driverClassName, factory)
        }
        if (drivers.isEmpty) Report.report("No ADBC driver found!")
    }

    private def getServiceLoader(classLoaderOfLoggerFactory: ClassLoader): ServiceLoader[ADBCServiceProvider] = {
        ServiceLoader.load(classOf[ADBCServiceProvider], classLoaderOfLoggerFactory)
    }

    private def safelyInstantiate(
        providerList: mutable.ArrayBuffer[ADBCServiceProvider],
        iterator: util.Iterator[ADBCServiceProvider]
    ): Unit = try {
        val provider = iterator.next()
        providerList.addOne(provider)
    } catch {
        case e: ServiceConfigurationError =>
            Report.report(s"An ADBC service provider failed to instantiate:\n${e.getMessage}", "ADBC")
    }

}
