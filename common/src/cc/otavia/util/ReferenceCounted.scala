/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.util

/** A reference-counted object that requires explicit deallocation.
 *
 *  When a new [[ReferenceCounted]] is instantiated, it starts with the reference count of 1. [[retain]] increases the
 *  reference count, and [[release]] decreases the reference count. If the reference count is decreased to 0, the object
 *  will be deallocated explicitly, and accessing the deallocated object will usually result in an access violation.
 *
 *  If an object that implements [[ReferenceCounted]] is a container of other objects that implement
 *  [[ReferenceCounted]], the contained objects will also be released via [[release]] when the container's reference
 *  count becomes 0.
 */
trait ReferenceCounted {

    /** Returns the reference count of this object. If 0, it means this object has been deallocated. */
    def refCnt: Int

    /** Increases the reference count by 1. */
    def retain: this.type

    /** Increases the reference count by the specified increment. */
    def retain(increment: Int): this.type

    /** Decreases the reference count by 1 and deallocates this object if the reference count reaches at 0.
     *  @return
     *    true if and only if the reference count became 0 and this object has been deallocated
     */
    def release: Boolean

    /** Decreases the reference count by the specified decrement and deallocates this object if the reference count
     *  reaches at 0.
     *  @param decrement
     *    decrement reference count
     *  @return
     *    true if and only if the reference count became 0 and this object has been deallocated
     */
    def release(decrement: Int): Boolean

}
