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

package io.otavia.core.util

import java.security.{AccessController, PrivilegedAction}
import scala.language.unsafeNulls
import scala.util.Try

/** A collection of utility methods to retrieve and parse the values of the Java system properties. */
object SystemPropertyUtil {

    /** Returns true if and only if the system property with the specified [[key]] exists. */
    def contains(key: String): Boolean = get(key).nonEmpty

    /** Returns the value of the Java system property with the specified [[key]], while falling back to [[None]] if the
     *  property access fails.
     *
     *  @return
     *    the property value
     */
    def get(key: String): Option[String] = {
        require(key.trim != "", "key must not be empty.")

        if (System.getSecurityManager == null) Option(System.getProperty(key))
        else
            AccessController.doPrivileged(new PrivilegedAction[Option[String]] {
                override def run(): Option[String] = Option(System.getProperty(key))
            })
    }

    /** Returns the value of the Java system property with the specified [[key]], while falling back to the specified
     *  default value if the property access fails.
     *
     *  @return
     *    the property value. [[default]] if there's no such property or if an access to the specified property is not
     *    allowed.
     */
    def get(key: String, default: String): String = get(key) match
        case Some(value) => value
        case None        => default

    def getBoolean(key: String, default: Boolean): Boolean = get(key) match {
        case None => default
        case Some(value) =>
            value.trim.toLowerCase match {
                case "true" | "yes" | "1" => true
                case "false" | "no" | "0" => false
                case _                    => default
            }
    }

    def getInt(key: String, default: Int): Int = get(key).map(_.toInt).getOrElse(default)

    def getLong(key: String, default: Long): Long = get(key).map(_.toLong).getOrElse(default)

    def readStringFromClassPath(path: String, bufferSize: Int = 8192): String = {
        val buffer = new Array[Byte](bufferSize)
        getClass.getResourceAsStream(path).read(buffer)
        new String(buffer)
    }

}
