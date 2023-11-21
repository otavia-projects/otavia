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

package cc.otavia.core.cache

import cc.otavia.core.cache.ActorThreadLocal.UNSET
import cc.otavia.core.system.ActorThread

abstract class ActorThreadLocal[V <: AnyRef] extends ThreadLocal[V] {

    private var variables: Array[AnyRef] = _

    private def initializeValue(index: Int): V = {
        val v = initialValue()
        initialTimer()
        variables(index) = v
        v
    }

    override private[cache] def doInitial(len: Int): Unit = {
        val arr = new Array[AnyRef](len)
        arr.indices.foreach { index => arr(index) = UNSET }
        variables = arr
    }

    final override def get(): V = {
        val index = threadIndex()
        if (variables(index) != UNSET) {
            updateGetTime(index)
            variables(index).asInstanceOf[V]
        } else initializeValue(index)
    }

    override def getIfExists: V | Null = {
        val index = threadIndex()
        if (variables(index) == UNSET) {
            null
        } else {
            updateGetTime(index)
            variables(index).asInstanceOf[V]
        }
    }

    override def set(v: V): Unit = {
        val index = threadIndex()
        if (variables(index) == UNSET) initialTimer()
        updateSetTime()
        variables(index) = v
    }

    override def isSet: Boolean = {
        val index = threadIndex()
        variables(index) != UNSET
    }

    override def remove(): Unit = if (isInitial) {
        val index = threadIndex()
        val v     = variables(index)
        variables(index) = UNSET
        if (v != UNSET) {
            cancelTimer()
            onRemoval(v.asInstanceOf[V])
        }
    }

}

object ActorThreadLocal {
    private val UNSET: AnyRef = new Object()
}
