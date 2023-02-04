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

/** Light-weight object pool.
 *  @tparam T
 *    the type of the pooled object
 */
abstract class ObjectPool[T <: Poolable] {

    protected def newObject(): T

    /** Get a object from the [[ObjectPool]]. The returned object may be created via [[newObject]] if no pooled object
     *  is ready to be reused.
     */
    def get(): T

    /** Recycle the object if possible and so make it ready to be reused.
     *  @param poolable
     *    [[Poolable]] object [[T]]
     */
    def recycle(poolable: T): Unit

}
