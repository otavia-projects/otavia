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

package io.otavia.log4a

import io.otavia.core.ioc
import io.otavia.core.slf4a.{AbstractILoggerFactory, LogLevel, Logger}
import io.otavia.core.system.ActorSystem
import io.otavia.log4a.config.RootLoggerConfig

import java.io.InputStream
import scala.language.unsafeNulls
import scala.xml.{Node, XML}

class Log4aLoggerFactory extends AbstractILoggerFactory {

    loadXML()

    private def loadXML(): Unit = {
        val stream: InputStream = Option(getClass.getResourceAsStream("/log4a.xml"))
            .getOrElse(getClass.getResourceAsStream("/log4a.default.xml"))

        val doc = XML.load(stream)

        val root = (doc \ "root").head

        val rootLoggerConfig = parseRootLogger(root)

        ???
    }

    private def parseRootLogger(root: Node): RootLoggerConfig = {
        val level = LogLevel.valueOf((root \@ "level").trim.toUpperCase)
        val refs  = (root \ "appender-ref").map(app => app \@ "ref")
        RootLoggerConfig(level, refs)
    }

    override def module: ioc.Module = ???

    override def getLogger0(name: String, system: ActorSystem): Logger = ???

}
