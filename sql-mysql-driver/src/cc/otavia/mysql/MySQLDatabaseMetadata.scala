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

package cc.otavia.mysql

import cc.otavia.sql.DatabaseMetadata

import scala.language.unsafeNulls

case class MySQLDatabaseMetadata(
    override val fullVersion: String,
    override val productName: String,
    override val majorVersion: Int,
    override val minorVersion: Int,
    microVersion: Int
) extends DatabaseMetadata

object MySQLDatabaseMetadata {
    def parse(version: String): MySQLDatabaseMetadata = {
        var serverVersion: String = version
        var majorVersion          = 0
        var minorVersion          = 0
        var microVersion          = 0
        val len                   = serverVersion.length
        val isMariaDb             = serverVersion.contains("MariaDB")
        val productName           = if (isMariaDb) "MariaDB" else "MySQL"

        // MariaDB server version < 11.x.x is by default prefixed by "5.5.5-"
        if (isMariaDb) serverVersion = serverVersion.replace("5.5.5-", "")

        var versionToken: String = null
        val versionTokenStartIdx = 0
        var versionTokenEndIdx   = 0
        var break                = false
        while (versionTokenEndIdx < len && !break) {
            val c = serverVersion.charAt(versionTokenEndIdx)
            if (c == '-' || c == ' ') {
                versionToken = serverVersion.substring(versionTokenStartIdx, versionTokenEndIdx)
                break = true
            }
            versionTokenEndIdx += 1
        }

        if (versionToken == null) { // if there's no '-' char
            versionToken = serverVersion
        }
        // we assume the server version tokens follows the syntax: ${major}.${minor}.${micro}
        val versionTokens = versionToken.split("\\.")
        try {
            majorVersion = versionTokens(0).toInt
            minorVersion = versionTokens(1).toInt
            microVersion = versionTokens(2).toInt
        } catch {
            case ex: Exception => // make sure it does fail the connection phase
        }
        new MySQLDatabaseMetadata(version, productName, majorVersion, minorVersion, microVersion)
    }
}
