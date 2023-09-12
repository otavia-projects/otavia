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

package cc.otavia.adbc

import java.util
import java.util.regex.{Matcher, Pattern}
import scala.language.unsafeNulls
import scala.util.matching.Regex

trait DriverFactory {

    protected val JDBC_URL_RE: Regex =
        "^jdbc:(mysql|mariadb|oracle|sqlserver|postgresql|sqlite):((\\/\\/[\\w.-]+(:\\d+)?\\/\\w+)|\\/[\\w.-]+)$".r

    protected val PROP_HOST     = "host"
    protected val PROP_PORT     = "port"
    protected val PROP_DATABASE = "database"
    protected val PROP_SERVER   = "server"
    protected val PROP_PARAMS   = "params"
    protected val PROP_FOLDER   = "folder"
    protected val PROP_FILE     = "file"
    protected val PROP_USER     = "user"
    protected val PROP_PASSWORD = "password"

    def newDriver(options: ConnectOptions): Driver

    def driverClassName: String

    def parseOptions(url: String, info: Map[String, String]): ConnectOptions

    private def getPropertyRegex(property: String): String = property match
        case PROP_FOLDER | PROP_FILE | PROP_PARAMS => ".+?"
        case _                                     => "[\\\\w\\\\-_.~]+"

    private def replaceAll(input: String, regex: String, replacer: Matcher => String): String = {
        val matcher          = Pattern.compile(regex).matcher(input)
        val sb: StringBuffer = new StringBuffer
        while (matcher.find) matcher.appendReplacement(sb, replacer(matcher))
        matcher.appendTail(sb)
        sb.toString
    }

    protected def getPattern(url: String): Pattern = {
        var pattern = url
        pattern = replaceAll(pattern, "\\[(.*?)]", (m) => "\\\\E(?:\\\\Q" + m.group(1) + "\\\\E)?\\\\Q")
        pattern = replaceAll(
          pattern,
          "\\{(.*?)}",
          m => "\\\\E(\\?<\\\\Q" + m.group(1) + "\\\\E>" + getPropertyRegex(m.group(1)) + ")\\\\Q"
        )
        pattern = "^\\Q" + pattern + "\\E$"
        Pattern.compile(pattern)
    }

}
