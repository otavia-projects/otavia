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

import scala.language.unsafeNulls

class QueueMap[V <: QueueMapEntity] {

    import QueueMap.*

    private var table: Array[QueueMapEntity] = new Array[QueueMapEntity](tableSizeFor(initialCapacity))
    private var mask: Int                    = tableSizeFor(initialCapacity) - 1
    private var head: QueueMapEntity         = _
    private var tail: QueueMapEntity         = _

    private var threshold: Int   = newThreshold(tableSizeFor(initialCapacity))
    private var contentSize: Int = 0

    private var barrier: Boolean = false

    private final def loadFactor: Double   = 2.0
    private final def initialCapacity: Int = 16
    private def newThreshold(size: Int)    = (size.toDouble * loadFactor).toInt

    private def index(id: Long): Int = (id & mask).toInt

    def isBarrierMode: Boolean              = barrier
    def setBarrierMode(mode: Boolean): Unit = barrier = mode

    def size: Int = contentSize

    def isEmpty: Boolean = contentSize == 0

    def nonEmpty: Boolean = contentSize != 0

    final private[core] def append(v: V): Unit = {
        if (contentSize == 0) {
            head = v
            tail = v
            put0(v)
        } else {
            tail.queueLater = v
            v.queueEarlier = tail
            tail = v
            put0(v)
        }
        contentSize += 1
    }

    final private[core] def pop(): V = if (contentSize == 1) {
        val entity = head
        head = null
        tail = null
        remove0(entity.entityId)
        entity.asInstanceOf[V]
    } else {
        val entity = head
        head = entity.queueLater
        head.queueEarlier = null
        entity.queueLater = null
        remove0(entity.entityId)
        entity.asInstanceOf[V]
    }

    final private[core] def remove(id: Long): V = {
        val entity = remove0(id)

        val pre  = entity.queueEarlier
        val next = entity.queueLater

        entity.cleanEntity()

        if (pre != null && next != null) {
            pre.queueLater = next
            next.queueEarlier = pre
        } else if (pre == null && next == null) {
            // do nothing
        } else if (pre == null) {
            head = next
            head.queueEarlier = null
        } else {
            tail = pre
            tail.queueLater = null
        }

        entity.asInstanceOf[V]
    }

    def borrow(id: Long): Option[V] = {
        val node = findNode(id)
        if (node != null) Some(node.asInstanceOf[V]) else None
    }

    def unsafeBorrow(id: Long): V = findNode(id).asInstanceOf[V]

    def contains(id: Long): Boolean = findNode(id) != null

    def isHead(value: V): Boolean = head == value

    def isTail(value: V): Boolean = tail == value

    def first: V = head.asInstanceOf[V]

    def last: V = tail.asInstanceOf[V]

    def headIsBarrier: Boolean = if (head != null && head.isBarrier) true else false

    private def findNode(id: Long): QueueMapEntity = {
        if (tail != null && tail.entityId == id) tail
        else if (table ne null) {
            table(index(id)) match
                case null   => null
                case entity => entity.findHashNode(id)
        } else null
    }

    private final def remove0(id: Long): QueueMapEntity = {
        val idx = index(id)
        val entity = table(idx) match
            case null => null
            case node if node.entityId == id =>
                table(idx) = node.hashNext
                contentSize -= 1
                node.hashNext = null
                node
            case node =>
                var prev   = node
                var cursor = node.hashNext
                while ((cursor ne null) && cursor.entityId != id) {
                    prev = cursor
                    cursor = cursor.hashNext
                }
                if (cursor ne null) {
                    prev.hashNext = cursor.hashNext
                    contentSize -= 1
                    cursor.hashNext = null
                }
                cursor

        if (table.length >= initialCapacity * 4 && contentSize < table.length / 2) resizeTable(table.length / 2)

        entity
    }

    final private def put0(entity: QueueMapEntity): Unit = {
        val idx = index(entity.entityId)
        table(idx) match
            case null => table(idx) = entity
            case old =>
                var tail = old
                while (tail.hashNext ne null) tail = tail.hashNext
                tail.hashNext = entity
    }

    final private def resizeTable(newLen: Int): Unit = {
        val oldTable = table
        table = new Array[QueueMapEntity](newLen)
        mask = newLen - 1
        threshold = newThreshold(table.length)

        for (node <- oldTable) {
            var cursor = node
            while (cursor ne null) {
                val entity = cursor
                cursor = cursor.hashNext
                entity.hashNext = null
                put0(entity)
            }
        }
    }

}

object QueueMap {
    private def tableSizeFor(capacity: Int): Int = (Integer.highestOneBit((capacity - 1).max(4)) * 2).min(1 << 30)
}
