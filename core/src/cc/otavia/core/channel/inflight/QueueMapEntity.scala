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

package cc.otavia.core.channel.inflight

import scala.annotation.tailrec
import scala.language.unsafeNulls

trait QueueMapEntity {

    private[inflight] var hashNext: QueueMapEntity     = _
    private[inflight] var queueEarlier: QueueMapEntity = _
    private[inflight] var queueLater: QueueMapEntity   = _

    def entityId: Long

    def isBarrier: Boolean

    @tailrec
    private[inflight] final def findHashNode(id: Long): QueueMapEntity = {
        if (id == entityId) this else if (hashNext eq null) null else hashNext.findHashNode(id)
    }

    def cleanEntity(): Unit = {
        hashNext = null
        queueEarlier = null
        queueLater = null
    }

}
