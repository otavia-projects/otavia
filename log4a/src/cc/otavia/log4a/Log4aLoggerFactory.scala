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

package cc.otavia.log4a

import cc.otavia.core.actor.ActorFactory
import cc.otavia.core.ioc
import cc.otavia.core.ioc.BeanDefinition
import cc.otavia.core.slf4a.{AbstractILoggerFactory, LogLevel, Logger}
import cc.otavia.core.system.ActorSystem
import cc.otavia.log4a.appender.ConsoleAppender
import cc.otavia.log4a.config.*

import java.io.InputStream
import scala.collection.mutable
import scala.language.unsafeNulls
import scala.xml.{Node, NodeSeq, XML}

class Log4aLoggerFactory extends AbstractILoggerFactory {

    private val log4aModule         = new Log4aModule()
    private val appenderMap         = new mutable.HashMap[String, String]()
    private var config: Log4aConfig = _

    private val refsMap  = new mutable.HashMap[String, Array[String]]()
    private var rootRefs = Array.empty[String]

    initFromXML()

    private def initFromXML(): Unit = {
        val stream: InputStream = Option(getClass.getResourceAsStream("/log4a.xml"))
            .getOrElse(getClass.getResourceAsStream("/log4a.default.xml"))

        val doc = XML.load(stream)

        val root = (doc \ "root").head

        val rootLoggerConfig = parseRootLogger(root)

        val normalLoggerConfigs = parseLoggers(doc \ "logger")

        val appenderConfigs = parseAppender(doc \ "appender")

        val config = Log4aConfig(appenderConfigs, rootLoggerConfig, normalLoggerConfigs)

        this.config = config

        constructModule(config)
        constructRefs()
    }

    private def parseRootLogger(node: Node): RootLoggerConfig = {
        val level = LogLevel.valueOf((node \@ "level").trim.toUpperCase)
        val refs  = (node \ "appender-ref").map(app => app \@ "ref")
        RootLoggerConfig(level, refs)
    }

    private def parseLoggers(loggers: NodeSeq): Seq[NormalLoggerConfig] = {
        loggers
            .map { node =>
                val prefix: String = node \@ "name"
                val level          = LogLevel.valueOf((node \@ "level").trim.toUpperCase)
                val additivity     = (node \@ "additivity").trim.toLowerCase.toBoolean
                val refs           = (node \ "appender-ref").map(app => app \@ "ref")
                NormalLoggerConfig(prefix, level, refs, additivity)
            }
            .sortBy(lo => lo.prefix.length)
    }

    private def parseAppender(appenders: NodeSeq): Seq[AppenderConfig] = {
        appenders.map { node =>
            val clz  = node \@ "class"
            val name = node \@ "name"
            if (clz == "cc.otavia.log4a.appender.ConsoleAppender") {
                ConsoleAppenderConfig(name, clz)
            } else {
                ???
            }
        }
    }

    private def constructModule(config: Log4aConfig): Unit = {
        config.appenderConfigs.foreach {
            case ConsoleAppenderConfig(name, clzName) =>
                val appenderName = name + "@" + clzName
                val qualifier    = Some(appenderName)
                appenderMap.put(name, appenderName)
                val factory = new ActorFactory[ConsoleAppender] {
                    override def newActor(): ConsoleAppender = new ConsoleAppender()
                }
                log4aModule.addDefinition(BeanDefinition(factory, qualifier = qualifier))
            case _ =>
        }
    }

    private def constructRefs(): Unit = {
        for (lo <- config.loggerConfigs) {
            refsMap.put(lo.prefix, lo.refs.map(ref => appenderMap(ref)).toArray)
        }
        rootRefs = config.rootConfig.refs.map(ref => appenderMap(ref)).toArray
    }

    override def module: ioc.Module = log4aModule

    override def getLogger0(name: String, system: ActorSystem): Log4aLogger = {
        config.loggerConfigs.find(c => name.startsWith(c.prefix)) match
            case Some(value) => new Log4aLogger(name, value.level, refsMap(value.prefix))
            case None        => new Log4aLogger(name, config.rootConfig.level, rootRefs)
    }

}
