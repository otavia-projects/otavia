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

trait Resource {}

object Resource {

    /** Attempt to dispose of whatever the given object is.
     *
     *  If the object is [[AutoCloseable]], such as anything that implements [[Resource]], then it will be closed. If
     *  the object is [[ReferenceCounted]], then it will be released once.
     *
     *  Any exceptions caused by this will be left to bubble up, and checked exceptions will be wrapped in a
     *  [[RuntimeException]].
     *
     *  @param obj
     *    The object to dispose of.
     */
    def dispose(obj: AnyRef): Unit = {
        obj match
            case closeable: AutoCloseable =>
                try closeable.close()
                catch {
                    case re: RuntimeException => throw re
                    case e: Exception         => throw new RuntimeException("Exception from closing object", e)
                }
            case _ => if (ReferenceCountUtil.isReferenceCounted(obj)) ReferenceCountUtil.release(obj)
    }

    def touch(obj: AnyRef, hint: AnyRef): Unit = {
        // TODO
    }

}
