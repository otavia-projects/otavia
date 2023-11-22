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

package cc.otavia.http.server

import cc.otavia.core.actor.AcceptorActor
import cc.otavia.core.cache.{ActorThreadLocal, ResourceTimer}
import cc.otavia.core.timer.TimeoutTrigger

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import java.util.Locale
import scala.language.unsafeNulls

class HttpServer(override val workerNumber: Int = 8, routers: Seq[Router], serverName: String = "otavia-http")
    extends AcceptorActor[HttpServerWorker] {

    private val routerMatcher = new RouterMatcher(routers)

    private val dates = new ActorThreadLocal[Array[Byte]] {

        private val formatter =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneId.of("GMT"))

        override protected def initialValue(): Array[Byte] = generateHttpHeaderDate()

        override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
            Some(TimeoutTrigger.DelayPeriod(1000, 1000))

        override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = set(generateHttpHeaderDate())

        private def generateHttpHeaderDate(): Array[Byte] = {
            val datetime = LocalDateTime.now(ZoneId.of("GMT"))
            val gmt      = datetime.format(formatter)
            s"Date: $gmt\r\n".getBytes(StandardCharsets.US_ASCII)
        }

    }

    override protected def workerFactory: AcceptorActor.WorkerFactory[HttpServerWorker] = () =>
        new HttpServerWorker(routerMatcher.sync(), dates, serverName)

}
