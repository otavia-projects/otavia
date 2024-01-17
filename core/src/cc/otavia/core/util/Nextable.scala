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

private[core] trait Nextable {

    @volatile private var n: Nextable | Null = _

    /** Set the next object of this object. */
    def next_=(next: Nextable): Unit = n = next

    def next: Nextable | Null = n

    def cleanNext(): Unit = n = null

    def isTail: Boolean = n == null

    /** true if and only if this object is not in any chain */
    def notInChain: Boolean = n == null

    def deChain(): Unit = n = null

}
