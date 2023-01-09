/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This class is fork from netty
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

package io.otavia.core.reactor.nio

import java.nio.channels.SelectionKey
import java.util
import java.util.Arrays

final class SelectedSelectionKeySet extends util.AbstractSet[SelectionKey] {

    private var keys: Array[SelectionKey] = new Array[SelectionKey](2048)
    private var _size: Int                = 0

    override def add(o: SelectionKey): Boolean = if (o != null) {
        if (size == keys.length) increaseCapacity()
        keys(_size) = o
        _size += 1
        true
    } else false

    override def remove(o: Any): Boolean = false

    override def contains(o: Any): Boolean = false

    override def size(): Int = _size

    override def iterator(): util.Iterator[SelectionKey] = new util.Iterator[SelectionKey] {
        private var idx: Int = 0

        override def hasNext: Boolean = idx < _size

        override def next(): SelectionKey = if (hasNext) {
            val key = keys(idx)
            idx += 1
            key
        } else throw new NoSuchElementException()

        override def remove(): Unit = throw new UnsupportedOperationException
    }

    def reset(start: Int = 0): Unit = if (start < _size && start >= 0) {
        var cursor = start
        while (cursor < _size) {
            keys(cursor) = null
            cursor += 1
        }
        _size = _size - start
    }

    private def increaseCapacity(): Unit = {
        val newKeys = new Array[SelectionKey](keys.length + 2048)
        System.arraycopy(keys, 0, newKeys, 0, _size)
        keys = newKeys
    }

}
