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

package cc.otavia.core.system.monitor

import cc.otavia.core.system.ActorSystem

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*
import scala.language.unsafeNulls

class SystemMonitorTask(val system: ActorSystem) extends Runnable {

    private var fs: Array[FileChannel] = _

    @volatile private var c: Boolean = false

    override def run(): Unit = if (!c) {
        if (system.pool.isInit) {
            if (fs == null) openLogFile()
            val time  = System.currentTimeMillis()
            val stats = system.monitor()
            stats.threadMonitor.actorThreadMonitors.zipWithIndex.foreach { case (m, d) =>
                val sample =
                    s"${time},${m.manager.mounts},${m.manager.serverActors},${m.manager.channelsActors},${m.manager.stateActors}\n"

                fs(d).write(ByteBuffer.wrap(sample.getBytes))
            }
        }
    }

    private def openLogFile(): Unit = fs = system.pool.workers.map { thread =>
        val file = new File(s"monitor.${thread.index}.log")
        FileChannel.open(file.toPath, CREATE, WRITE, TRUNCATE_EXISTING)
    }

    def close(): Unit = {
        c = true
        fs.foreach(_.close())
        fs = null
    }

}
