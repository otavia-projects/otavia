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

package io.otavia.core.system

import io.otavia.core.system.ActorHousePhantomRef.refSet

import java.util.concurrent.CopyOnWriteArraySet
import scala.ref.{PhantomReference, ReferenceQueue}

class ActorHousePhantomRef(val house: ActorHouse, val queue: ReferenceQueue[ActorHouse])
    extends PhantomReference[ActorHouse](house, queue) {

    private val runnable: Runnable = () => house.close()

    refSet.add(this)

    override def clear(): Unit = {
        runnable.run()
        refSet.remove(this)
        super.clear()
    }

}

object ActorHousePhantomRef {
    private val refSet = new CopyOnWriteArraySet[ActorHousePhantomRef]()
}
