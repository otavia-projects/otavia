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

import cc.otavia.core.cache.Poolable
import cc.otavia.core.message.ExceptionMessage
import cc.otavia.core.util.Chainable

/** Promise is an object which can be completed with a value or failed with an exception.
 *
 *  A promise should always eventually be completed, whether for success or failure, in order to avoid unintended
 *  resource retention for any associated Futures' callbacks or transformations.
 *
 *  @tparam V
 *    type of result
 */
private[core] trait Promise[V] {

    /** Marks this promise as a success.
     *
     *  If it is success or failed already it will throw an [[IllegalStateException]].
     */
    def setSuccess(result: V): Promise[V]

    /** Marks this promise as a failure.
     *
     *  If it is success or failed already it will throw an [[IllegalStateException]].
     */
    def setFailure(cause: Throwable): Promise[V]

    /** Return the [[Future]] instance is associated with this promise. This future will be completed upon completion of
     *  this promise.
     *
     *  @return
     *    A future instance associated with this promise.
     */
    def future: Future[V]

    /** Whether this promise is set to be time-out
     *  @return
     *    true if it has be set.
     */
    def canTimeout: Boolean

    def setStack(s: Stack): Unit

    def actorStack: Stack

}
