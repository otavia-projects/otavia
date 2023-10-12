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

package cc.otavia.core.message

import cc.otavia.core.address.Address

class IdAllocator private[core] () {

    private var address: Address[Call] = _
    private var aid: Long              = _
    private var currentId: Long        = Long.MinValue

    private[core] def setActorId(id: Long): Unit = aid = id

    private[core] def setActorAddress(address: Address[? <: Call]): Unit =
        this.address = address.asInstanceOf[Address[Call]]

    def sender: Address[Call] = address

    def actorId: Long = aid

    def generate: Long = {
        val id = currentId
        currentId += 1
        id
    }

}
