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

/** An [[IllegalStateException]] which is raised when a user attempts to access a [[ReferenceCounted]] whose reference
 *  count has been decreased to 0 (and consequently freed).
 */
class IllegalReferenceCountException(message: String | Null, cause: Throwable | Null)
    extends IllegalStateException(message, cause) {

    /** Creates a new instance. */
    def this() = this(null, null)

    /** Creates a new instance. */
    def this(message: String) = this(message, null)

    /** Creates a new instance. */
    def this(cause: Throwable) = this(null, cause)

    def this(refCnt: Int) = this("refCnt: " + refCnt)

    def this(refCnt: Int, increment: Int) =
        this(
          s"refCnt: $refCnt, ${if (increment > 0) s"increment: $increment" else s"decrement: -$increment"}"
        )

}