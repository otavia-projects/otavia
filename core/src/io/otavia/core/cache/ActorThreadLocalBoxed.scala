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

package io.otavia.core.cache

import io.otavia.core.cache.ActorThreadLocalBoxed.ValueBox
import io.otavia.core.system.ActorThread

abstract class ActorThreadLocalBoxed[V] extends ThreadLocal[V] {

    private var variables: Array[AnyRef] = ThreadLocal.EMPTY // Use boxed objects to avoid cpu cache false sharing.

    private def valueBox(index: Int): ValueBox[V] = variables(index).asInstanceOf[ValueBox[V]]

    override private[cache] def doInit(len: Int): Unit = {
        val arr = new Array[AnyRef](len)
        arr.indices.foreach { index =>
            val box: ValueBox[V] = ValueBox()
            arr(index) = box
        }
        variables = arr
    }

    private def initializeValue(box: ValueBox[V]): V = {
        val v = initialValue()
        box.set(v)
        v
    }

    final def get(): V = {
        val index = threadIndex()
        val box   = valueBox(index)
        box.get match
            case v: V       => v
            case null: Null => initializeValue(box)
    }

    final def getIfExists: V | Null = {
        val index = threadIndex()
        val box   = valueBox(index)
        box.get
    }

    final def set(value: V): Unit = {
        val index = threadIndex()
        val box   = valueBox(index)
        box.set(value)
    }

    override def isSet: Boolean = {
        val index = threadIndex()
        val box   = valueBox(index)
        box match
            case null: Null     => false
            case v: ValueBox[V] => v.nonEmpty
    }

    override def remove(): Unit = if (isInited) {
        val index = threadIndex()
        val box   = valueBox(index)
        box.get match
            case null: Null =>
            case v: V =>
                box.remove()
                onRemoval(v)
    }

}

object ActorThreadLocalBoxed {

    private class ValueBox[V] {

        private var value: V | Null = null

        def set(v: V): Unit = value = v
        def get: V | Null   = value

        def remove(): Unit = value = null

        def isEmpty: Boolean = value == null

        def nonEmpty: Boolean = value != null

    }

    private object ValueBox {
        def apply[V](): ValueBox[V] = new ValueBox()
    }

}
