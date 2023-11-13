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

object ReferenceCountUtil {

    /** Tests whether the given object implements the [[ReferenceCounted]] interface.
     *  @param obj
     *    The object to test.
     *  @return
     *    true if the given object is reference counted.
     */
    final def isReferenceCounted(obj: AnyRef): Boolean = obj.isInstanceOf[ReferenceCounted]

    /** Try to call [[ReferenceCounted]].release if the specified message implements [[ReferenceCounted]]. If the
     *  specified message doesn't implement [[ReferenceCounted]], this method does nothing.
     */
    final def release(msg: AnyRef): Boolean = msg match
        case referenceCounted: ReferenceCounted => referenceCounted.release
        case _                                  => false

}
