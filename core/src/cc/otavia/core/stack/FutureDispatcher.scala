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

package cc.otavia.core.stack

import scala.language.unsafeNulls

/** This abstract class is extend by [[cc.otavia.core.actor.AbstractActor]] for manage [[Future]]s like
 *  [[scala.collection.mutable.HashMap]]. We don't use hashmap because is box/unbox [[Long]] as key, this class is avoid
 *  this cost.
 */
private[core] abstract class FutureDispatcher {

    import FutureDispatcher.*

    private var table: Array[AbstractPromise[?]] = _
    private var mask: Int                        = tableSizeFor(initialCapacity) - 1

    private var threshold: Int = newThreshold(tableSizeFor(initialCapacity))

    private var contentSize: Int = 0

    private final def loadFactor: Double   = 2.0
    private final def initialCapacity: Int = 16

    private def newThreshold(size: Int) = (size.toDouble * loadFactor).toInt

    inline private def index(id: Long): Int = (id & mask).toInt

    inline private def findNode(id: Long): AbstractPromise[?] = {
        if (table ne null) {
            table(index(id)) match
                case null    => null
                case promise => promise.findNode(id)
        } else null
    }

    final protected def push(promise: AbstractPromise[?]): Unit = {
        if (table eq null) table = new Array[AbstractPromise[?]](tableSizeFor(initialCapacity))
        else if (contentSize + 1 >= threshold) resizeTable(table.length * 2)
        put0(promise)
    }

    private final def put0(promise: AbstractPromise[?]): Unit = {
        val idx = index(promise.id)
        table(idx) match
            case null => table(idx) = promise
            case old =>
                var tail = old
                while (tail._next ne null) tail = tail._next
                tail._next = promise
    }

    final protected def pop(id: Long): AbstractPromise[?] = {
        val idx = index(id)
        val promise = table(idx) match
            case null => null
            case node if node.id == id =>
                table(idx) = node._next
                contentSize -= 1
                node._next = null
                node
            case node =>
                var prev   = node
                var cursor = node._next
                while ((cursor ne null) && cursor.id != id) {
                    prev = cursor
                    cursor = cursor._next
                }
                if (cursor ne null) {
                    prev._next = cursor._next
                    contentSize -= 1
                    cursor._next = null
                }
                cursor

        if (table.length >= initialCapacity * 4 && contentSize < table.length / 2) { // shrinkage the hash table
            resizeTable(table.length / 2)
        }
        promise
    }

    final protected def contains(id: Long): Boolean = findNode(id) ne null

    private final def resizeTable(newLen: Int): Unit = {
        val oldTable = table
        table = new Array[AbstractPromise[?]](newLen)
        mask = newLen - 1
        threshold = newThreshold(table.length)

        for (node <- oldTable) {
            var cursor = node
            while (cursor ne null) {
                val promise = cursor
                cursor = cursor._next
                promise._next = null
                put0(promise)
            }
        }
    }

}

private[core] object FutureDispatcher {
    private[stack] def tableSizeFor(capacity: Int): Int =
        (Integer.highestOneBit((capacity - 1).max(4)) * 2).min(1 << 30)

}
