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

package cc.otavia.postgres

import cc.otavia.sql.DatabaseMetadata
import cc.otavia.postgres

import java.util.regex.{Matcher, Pattern}
import scala.language.unsafeNulls

case class PostgresDatabaseMetadata(
    override val fullVersion: String,
    override val majorVersion: Int,
    override val minorVersion: Int
) extends DatabaseMetadata {
    override def productName: String = "PostgreSQL"
}

object PostgresDatabaseMetadata {

    private val VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?")

    final def parse(serverVersion: String): PostgresDatabaseMetadata = {
        val matcher      = VERSION_PATTERN.matcher(serverVersion)
        var majorVersion = 0
        var minorVersion = 0
        if (matcher.find) {
            majorVersion = matcher.group(1).toInt
            val minorPart = matcher.group(2)
            if (minorPart != null) minorVersion = minorPart.toInt
        }
        new PostgresDatabaseMetadata(serverVersion, majorVersion, minorVersion)
    }

}
