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

package cc.otavia.core.util

/** An object which can be chained one by one */
trait Chainable {

    private var n: Chainable | Null = _
    private var p: Chainable | Null = _

    /** Set the next object of this object. */
    def next_=(chainable: Chainable): Unit = n = chainable

    def cleanNext(): Unit = n = null

    /** Get the next object of this object. */
    def next: Chainable | Null = n

    /** Set the pre object of this object. */
    def pre_=(chainable: Chainable): Unit = p = chainable

    def cleanPre(): Unit = p = null

    /** Get the pre object of this object. */
    def pre: Chainable | Null = p

    /** `true` if this object is at tail of a chain. */
    def isTail: Boolean = n == null

    /** `true` if this object is at head of a chain. */
    def isHead: Boolean = p == null

    /** true if and only if this object is not in any chain */
    def notInChain: Boolean = n == null && p == null

    def deChain(): Unit = {
        n = null
        p = null
    }

}
